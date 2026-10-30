package com.civicrepairs.adapters.out.vision;

import com.civicrepairs.adapters.out.metering.ModelMetering;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.ModelCallLedgerPort;
import com.civicrepairs.application.port.out.ModelCallLedgerPort.ModelCall;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.application.port.out.ModelUnavailableException.Reason;
import com.civicrepairs.application.port.out.VisionPort;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;

/** Calls vision-service (SAM 3.1 + anonymization). Results are cached by photo hash and damage type. */
@Component
@ConditionalOnExpression("!'${civic.vision.url:}'.isBlank()")
class VisionServiceAdapter implements VisionPort {

	/** Part of the cache key: bump it when vision-service changes its SAM phrases or parameters. */
	static final String VERSION = "vision-analyze.v2";

	private final RestClient http;
	private final JsonMapper json;
	private final ModelCallLedgerPort ledger;
	private final ModelMetering metering;

	VisionServiceAdapter(RestClient.Builder builder, @Value("${civic.vision.url}") String baseUrl, JsonMapper json,
			ModelCallLedgerPort ledger, ModelMetering metering) {
		// HTTP/1.1 only: the JDK client otherwise sends "Upgrade: h2c", and uvicorn then ignores the request body.
		var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.build());
		requestFactory.setReadTimeout(Duration.ofSeconds(120));
		this.http = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
		this.json = json;
		this.ledger = ledger;
		this.metering = metering;
	}

	@Override
	public VisionResult analyze(MediaContent photo, DamageType damageType) {
		String key = ModelMetering.cacheKey(VERSION, photo.media().sha256(), damageType.name());
		var cached = ledger.cachedResponse(key);
		if (cached.isPresent()) {
			return parse(json.readTree(cached.get()));
		}

		metering.ensureBudget();
		var imageHeaders = new HttpHeaders();
		imageHeaders.setContentType(MediaType.parseMediaType(photo.media().contentType()));
		imageHeaders.setContentDispositionFormData("image", "photo");
		var form = new LinkedMultiValueMap<String, Object>();
		form.add("image", new HttpEntity<>(new ByteArrayResource(photo.data()), imageHeaders));
		form.add("damage_type", damageType.name());

		String raw;
		try {
			raw = http.post()
					.uri("/v1/analyze")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(form)
					.retrieve()
					.body(String.class);
		} catch (RestClientException e) {
			ledger.record(new ModelCall(key, "VISION_ANALYZE", "sam-3.1", null, 0, 0, 0, BigDecimal.ZERO, false));
			throw new ModelUnavailableException(Reason.ERROR, "vision-service failed: " + e.getMessage(), e);
		}

		JsonNode response = json.readTree(raw);
		JsonNode usage = response.path("usage");
		ledger.record(new ModelCall(key, "VISION_ANALYZE", "sam-3.1", raw, 0, 0, usage.path("samImages").asInt(0),
				usage.path("costUsd").decimalValue(), true));
		return parse(response);
	}

	private static VisionResult parse(JsonNode r) {
		JsonNode damage = r.path("damage");
		return new VisionResult(
				damage.path("areaFraction").asDouble(),
				damage.path("detections").asInt(),
				r.path("faces").asInt(),
				r.path("plates").asInt(),
				Base64.getDecoder().decode(r.path("blurredJpegBase64").asString()),
				Base64.getDecoder().decode(r.path("markedJpegBase64").asString()));
	}
}
