package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.adapters.out.metering.ModelMetering;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.config.MetaApiProperties;
import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import com.civicrepairs.support.InMemoryModelCallLedger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A local HTTP server stands in for the Meta API: these tests never spend credits. */
class MuseLanguageModelAdapterTest {

	private final JsonMapper json = JsonMapper.builder().build();
	private final InMemoryModelCallLedger ledger = new InMemoryModelCallLedger();
	private final Deque<String> replies = new ArrayDeque<>();
	private final List<String> requests = new ArrayList<>();
	private HttpServer server;
	private MuseLanguageModelAdapter adapter;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] body = replies.removeFirst().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		adapter = adapterWithBudget(new BigDecimal("120"));
	}

	private MuseLanguageModelAdapter adapterWithBudget(BigDecimal budget) {
		var properties = new MetaApiProperties(true, "test-key",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "muse-spark-1.3", budget,
				new BigDecimal("1.25"), new BigDecimal("0.15"), new BigDecimal("4.25"));
		return new MuseLanguageModelAdapter(RestClient.builder(), json, ledger, new ModelMetering(ledger, properties),
				properties);
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static String completion(String content, int promptTokens, int completionTokens) {
		String escaped = content == null ? "null" : "\"" + content.replace("\"", "\\\"").replace("\n", " ") + "\"";
		return """
				{"choices":[{"message":{"role":"assistant","content":%s}}],
				 "usage":{"prompt_tokens":%d,"completion_tokens":%d,"prompt_tokens_details":{"cached_tokens":0}}}
				""".formatted(escaped, promptTokens, completionTokens);
	}

	private static MediaContent photo(String sha) {
		var media = new Media(UUID.randomUUID(), MediaKind.PHOTO, Visibility.PRIVATE, "image/jpeg", sha, 3, null);
		return new MediaContent(media, new byte[] {1, 2, 3});
	}

	@Test
	void sendsPromptSchemaAndImageAndRecordsTheCost() {
		replies.add(completion(MuseOutputParserTest.VALID, 1000, 200));

		var interpretation = adapter.interpret(photo("abc"), "Muy profundo");

		assertThat(interpretation.summary()).isEqualTo("Hueco profundo en el carril derecho.");
		var request = json.readTree(requests.getFirst());
		assertThat(request.path("model").asString()).isEqualTo("muse-spark-1.3");
		assertThat(request.path("response_format").path("type").asString()).isEqualTo("json_schema");
		assertThat(request.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
		assertThat(request.path("messages").path(0).path("content").asString()).contains("Bogotá");
		assertThat(requests.getFirst()).contains("Muy profundo").contains("data:image/");
		// 1000 * 1.25 / 1M + 200 * 4.25 / 1M
		assertThat(ledger.totalSpentUsd()).isEqualByComparingTo("0.00210");
	}

	@Test
	void sameInputNeverReachesTheModelTwice() {
		replies.add(completion(MuseOutputParserTest.VALID, 1000, 200));

		adapter.interpret(photo("abc"), "Muy profundo");
		adapter.interpret(photo("abc"), "Muy profundo");

		assertThat(requests).hasSize(1);
	}

	@Test
	void retriesOnceOnInvalidOutput() {
		replies.add(completion("{\"nope\":true}", 1000, 50));
		replies.add(completion(MuseOutputParserTest.VALID, 1000, 200));

		assertThat(adapter.interpret(photo("abc"), null).damageType().name()).isEqualTo("OPEN_MANHOLE");
		assertThat(requests).hasSize(2);
		assertThat(ledger.calls()).extracting("success").containsExactly(false, true);
	}

	@Test
	void givesUpAfterTwoInvalidOutputs() {
		replies.add(completion(null, 1000, 2000));
		replies.add(completion("not json", 1000, 50));

		assertThatThrownBy(() -> adapter.interpret(photo("abc"), null))
				.isInstanceOfSatisfying(ModelUnavailableException.class,
						e -> assertThat(e.reason()).isEqualTo(ModelUnavailableException.Reason.INVALID_OUTPUT));
		assertThat(ledger.calls()).hasSize(2).allMatch(call -> !call.success());
	}

	@Test
	void stopsCallingOnceTheBudgetIsSpent() {
		var broke = adapterWithBudget(BigDecimal.ZERO);

		assertThatThrownBy(() -> broke.interpret(photo("abc"), null))
				.isInstanceOfSatisfying(ModelUnavailableException.class,
						e -> assertThat(e.reason()).isEqualTo(ModelUnavailableException.Reason.BUDGET_EXHAUSTED));
		assertThat(requests).isEmpty();
	}
}
