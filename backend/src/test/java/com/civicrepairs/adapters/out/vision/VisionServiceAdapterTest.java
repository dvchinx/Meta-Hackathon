package com.civicrepairs.adapters.out.vision;

import com.civicrepairs.adapters.out.metering.ModelMetering;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.config.MetaApiProperties;
import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import com.civicrepairs.domain.shared.DamageType;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisionServiceAdapterTest {

	private static final String REPLY = """
			{"width":960,"height":720,
			 "damage":{"detections":2,"areaPx":79110,"areaFraction":0.114,"confidence":0.93,"boxes":[]},
			 "faces":0,"plates":1,
			 "blurredJpegBase64":"%s","markedJpegBase64":"%s",
			 "usage":{"samImages":3,"costUsd":0.0075}}
			""".formatted(Base64.getEncoder().encodeToString(new byte[] {1}),
			Base64.getEncoder().encodeToString(new byte[] {2}));

	private final InMemoryModelCallLedger ledger = new InMemoryModelCallLedger();
	private final List<String> contentTypes = new ArrayList<>();
	private final List<String> upgrades = new ArrayList<>();
	private final List<String> bodies = new ArrayList<>();
	private HttpServer server;
	private VisionServiceAdapter adapter;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/analyze", exchange -> {
			contentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
			upgrades.add(String.valueOf(exchange.getRequestHeaders().getFirst("Upgrade")));
			bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
			byte[] body = REPLY.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		var properties = new MetaApiProperties(true, "k", "http://unused", "muse-spark-1.3", new BigDecimal("120"),
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
		adapter = new VisionServiceAdapter(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
				JsonMapper.builder().build(), ledger, new ModelMetering(ledger, properties));
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private static MediaContent photo() {
		var media = new Media(UUID.randomUUID(), MediaKind.PHOTO, Visibility.PRIVATE, "image/jpeg", "sha-1", 4, null);
		return new MediaContent(media, new byte[] {(byte) 0xFF, (byte) 0xD8, 7, 7});
	}

	@Test
	void sendsAMultipartFormWithTheImageAndDamageType() {
		var result = adapter.analyze(photo(), DamageType.POTHOLE);

		assertThat(contentTypes.getFirst()).startsWith("multipart/form-data;").contains("boundary=");
		// uvicorn drops the body of requests asking to upgrade to h2c.
		assertThat(upgrades.getFirst()).isEqualTo("null");
		assertThat(bodies.getFirst())
				.contains("name=\"image\"; filename=")
				.contains("Content-Type: image/jpeg")
				.contains("name=\"damage_type\"")
				.contains("POTHOLE");
		assertThat(result.areaFraction()).isEqualTo(0.114);
		assertThat(result.plates()).isEqualTo(1);
		assertThat(result.blurredJpeg()).containsExactly(1);
		assertThat(ledger.totalSpentUsd()).isEqualByComparingTo("0.0075");
	}

	@Test
	void cachesByPhotoAndDamageType() {
		adapter.analyze(photo(), DamageType.POTHOLE);
		adapter.analyze(photo(), DamageType.POTHOLE);
		adapter.analyze(photo(), DamageType.OPEN_MANHOLE);

		assertThat(bodies).hasSize(2);
	}
}
