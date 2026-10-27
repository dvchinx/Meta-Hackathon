package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.adapters.out.metering.ModelMetering;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.ModelCallLedgerPort;
import com.civicrepairs.application.port.out.ModelCallLedgerPort.ModelCall;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.application.port.out.ModelUnavailableException.Reason;
import com.civicrepairs.config.MetaApiProperties;
import com.civicrepairs.domain.report.Interpretation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Muse Spark through the Chat Completions API. Every call: cache lookup → budget check → request → schema
 * validation (one retry) → cost recorded in the ledger, successful or not.
 */
@Component
@ConditionalOnProperty(name = "civic.meta.enabled", havingValue = "true")
class MuseLanguageModelAdapter implements LanguageModelPort {

	private static final Logger log = LoggerFactory.getLogger(MuseLanguageModelAdapter.class);
	private static final int MAX_IMAGE_SIDE = 1024;
	private static final int MAX_ATTEMPTS = 2;
	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	private final RestClient http;
	private final JsonMapper json;
	private final ModelCallLedgerPort ledger;
	private final ModelMetering metering;
	private final MetaApiProperties properties;
	private final MuseOutputParser parser;
	private final MusePrompt interpretPrompt = MusePrompt.load("interpret-report.v1");
	private final MusePrompt sameDamagePrompt = MusePrompt.load("same-damage.v1");

	MuseLanguageModelAdapter(RestClient.Builder builder, JsonMapper json, ModelCallLedgerPort ledger,
			ModelMetering metering, MetaApiProperties properties) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new IllegalStateException("META_API_ENABLED=true but META_API_KEY is empty");
		}
		var requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(90));
		this.http = builder.clone()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
				.build();
		this.json = json;
		this.ledger = ledger;
		this.metering = metering;
		this.properties = properties;
		this.parser = new MuseOutputParser(json);
	}

	@Override
	public Interpretation interpret(MediaContent photo, String context) {
		String text = context == null || context.isBlank() ? "(El ciudadano no escribió texto.)" : context;
		String key = ModelMetering.cacheKey("interpret", interpretPrompt.version(), properties.museModel(),
				photo.media().sha256(), ModelMetering.sha256(text));
		List<Object> userContent = List.of(textPart("Texto del ciudadano:\n" + text), imagePart(photo));
		return call("INTERPRET_REPORT", key, interpretPrompt, userContent, 2000, parser::interpretation);
	}

	@Override
	public SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate) {
		String key = ModelMetering.cacheKey("same-damage", sameDamagePrompt.version(), properties.museModel(),
				existing.media().sha256(), candidate.media().sha256());
		List<Object> userContent = List.of(textPart("Image 1 (existing report):"), imagePart(existing),
				textPart("Image 2 (new report):"), imagePart(candidate));
		return call("SAME_DAMAGE", key, sameDamagePrompt, userContent, 1500, parser::sameDamage);
	}

	private <T> T call(String purpose, String cacheKey, MusePrompt prompt, List<Object> userContent, int maxTokens,
			Function<String, T> parse) {
		var cached = ledger.cachedResponse(cacheKey);
		if (cached.isPresent()) {
			try {
				return parse.apply(cached.get());
			} catch (InvalidModelOutputException e) {
				log.warn("Ignoring unparseable cached {} response: {}", purpose, e.getMessage());
			}
		}

		Map<String, Object> body = requestBody(prompt, userContent, maxTokens);
		InvalidModelOutputException lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			metering.ensureBudget();
			JsonNode response = send(purpose, cacheKey, body);
			String content = response.path("choices").path(0).path("message").path("content").asString(null);
			try {
				T result = parse.apply(content);
				record(purpose, cacheKey, response, content, true);
				return result;
			} catch (InvalidModelOutputException e) {
				record(purpose, cacheKey, response, null, false);
				log.warn("{} attempt {} returned invalid output: {}", purpose, attempt, e.getMessage());
				lastError = e;
			}
		}
		throw new ModelUnavailableException(Reason.INVALID_OUTPUT, lastError.getMessage(), lastError);
	}

	private JsonNode send(String purpose, String cacheKey, Map<String, Object> body) {
		try {
			String raw = http.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
			return json.readTree(raw);
		} catch (RestClientException e) {
			ledger.record(new ModelCall(cacheKey, purpose, properties.museModel(), null, 0, 0, 0, BigDecimal.ZERO,
					false));
			throw new ModelUnavailableException(Reason.ERROR, purpose + " request failed: " + e.getMessage(), e);
		}
	}

	private Map<String, Object> requestBody(MusePrompt prompt, List<Object> userContent, int maxTokens) {
		Map<String, Object> schema = Map.of(
				"name", prompt.version().replace('.', '_').replace('-', '_'),
				"strict", true,
				"schema", json.readValue(prompt.schemaJson(), Map.class));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.museModel());
		body.put("messages", List.of(
				Map.of("role", "system", "content", prompt.instructions()),
				Map.of("role", "user", "content", userContent)));
		body.put("response_format", Map.of("type", "json_schema", "json_schema", schema));
		// Muse Spark always reasons; leave room for it or the answer comes back empty.
		body.put("max_completion_tokens", maxTokens);
		body.put("reasoning_effort", "low");
		return body;
	}

	private void record(String purpose, String cacheKey, JsonNode response, String content, boolean success) {
		JsonNode usage = response.path("usage");
		int input = usage.path("prompt_tokens").asInt(0);
		int cachedInput = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
		int output = usage.path("completion_tokens").asInt(0);
		BigDecimal cost = properties.museInputPerMillion().multiply(BigDecimal.valueOf(input - cachedInput))
				.add(properties.museCachedInputPerMillion().multiply(BigDecimal.valueOf(cachedInput)))
				.add(properties.museOutputPerMillion().multiply(BigDecimal.valueOf(output)))
				.divide(MILLION, MathContext.DECIMAL64);
		ledger.record(new ModelCall(cacheKey, purpose, properties.museModel(), content, input, output, 0, cost,
				success));
	}

	private static Map<String, Object> textPart(String text) {
		return Map.of("type", "text", "text", text);
	}

	private static Map<String, Object> imagePart(MediaContent media) {
		var jpeg = ImageDownscaler.toJpeg(media.data(), media.media().contentType(), MAX_IMAGE_SIDE);
		String url = "data:" + jpeg.contentType() + ";base64," + Base64.getEncoder().encodeToString(jpeg.data());
		return Map.of("type", "image_url", "image_url", Map.of("url", url));
	}
}
