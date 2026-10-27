package com.civicrepairs.adapters.in.whatsapp;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.application.port.out.MessagingPort;
import com.civicrepairs.support.InMemoryMessaging;
import com.civicrepairs.support.InMemoryMessaging.Sent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole WhatsApp conversation through the webhook, with model fakes, real PostGIS and a recorded outbox. */
@Import({TestcontainersConfiguration.class, WhatsAppWebhookFlowTest.Fakes.class})
@SpringBootTest(properties = {
		"META_API_ENABLED=false", "VISION_SERVICE_URL=",
		"WHATSAPP_APP_SECRET=test-app-secret", "WHATSAPP_VERIFY_TOKEN=verify-me",
		"WHATSAPP_PHONE_NUMBER_ID=PHONE_ID", "PUBLIC_BASE_URL=https://civic.example"})
class WhatsAppWebhookFlowTest {

	private static final String CITIZEN = "573001112233";
	private static final String SECRET = "test-app-secret";

	@TempDir
	static Path mediaDir;

	@DynamicPropertySource
	static void mediaPath(DynamicPropertyRegistry registry) {
		registry.add("civic.media.path", () -> mediaDir.toString());
	}

	@TestConfiguration
	static class Fakes {
		@Bean
		@Primary
		InMemoryMessaging inMemoryMessaging() {
			return new InMemoryMessaging();
		}

		/** Runs handling inline so assertions see the replies right after the webhook call. */
		@Bean
		@Primary
		MessageDispatcher synchronousDispatcher() {
			return (key, task) -> task.run();
		}
	}

	@Autowired
	WebApplicationContext context;
	@Autowired
	InMemoryMessaging messaging;
	@Autowired
	JdbcClient jdbc;

	MockMvc mvc;
	private final AtomicInteger ids = new AtomicInteger();

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		messaging.clear();
		for (String table : new String[] {"report", "status_transition", "issue", "model_call", "media",
				"processed_message"}) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}

	private static String envelope(String messageJson) {
		return """
				{"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
				 "messaging_product":"whatsapp","metadata":{"display_phone_number":"15551387806","phone_number_id":"PHONE_ID"},
				 "contacts":[{"profile":{"name":"Ana"},"wa_id":"%s"}],
				 "messages":[%s]}}]}]}
				""".formatted(CITIZEN, messageJson);
	}

	private String message(String type, String body) {
		return """
				{"from":"%s","id":"wamid.%d","timestamp":"1793100000","type":"%s",%s}
				""".formatted(CITIZEN, ids.incrementAndGet(), type, body);
	}

	private ResultActions deliver(String payload) throws Exception {
		byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
		return mvc.perform(post("/webhooks/whatsapp")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Hub-Signature-256", WebhookSignature.sign(SECRET, bytes))
				.content(bytes));
	}

	private ResultActions text(String body) throws Exception {
		return deliver(envelope(message("text", "\"text\":{\"body\":\"" + body + "\"}")));
	}

	private ResultActions photo() throws Exception {
		return deliver(envelope(message("image",
				"\"image\":{\"id\":\"media-1\",\"mime_type\":\"image/jpeg\",\"caption\":\"Hueco enorme\"}")));
	}

	private ResultActions location(double lat, double lon) throws Exception {
		return deliver(envelope(message("location",
				"\"location\":{\"latitude\":" + lat + ",\"longitude\":" + lon + "}")));
	}

	private ResultActions button(String id) throws Exception {
		return deliver(envelope(message("interactive",
				"\"interactive\":{\"type\":\"button_reply\",\"button_reply\":{\"id\":\"" + id + "\",\"title\":\"x\"}}")));
	}

	private List<String> replies() {
		return messaging.sent().stream().map(Sent::text).toList();
	}

	@Test
	void verificationHandshake() throws Exception {
		mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "verify-me")
						.param("hub.challenge", "1158201444"))
				.andExpect(status().isOk())
				.andExpect(content().string("1158201444"));
		mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
						.param("hub.challenge", "x"))
				.andExpect(status().isForbidden());
	}

	@Test
	void unsignedDeliveriesAreRejected() throws Exception {
		mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
						.content(envelope(message("text", "\"text\":{\"body\":\"hola\"}"))))
				.andExpect(status().isUnauthorized());
		assertThat(messaging.sent()).isEmpty();
	}

	@Test
	void fullConversationEndsInAnIssueOnTheMap() throws Exception {
		text("Hola").andExpect(status().isOk());
		assertThat(replies().getLast()).contains("¡Hola!").contains("foto").contains("ubicación");

		photo();
		assertThat(replies().getLast()).contains("Ahora envíame la ubicación");

		messaging.clear();
		location(4.676389, -74.048611);
		assertThat(messaging.sent()).extracting(Sent::kind).containsExactly("text", "image", "buttons");
		assertThat(messaging.sent().get(1).text()).contains("Hueco").contains("Instituto de Desarrollo Urbano");

		button(MessagingPort.CONFIRM_YES);
		assertThat(replies().getLast()).contains("quedó registrado").contains("https://civic.example");

		mvc.perform(get("/api/issues")).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void everythingInOneGoAndTypedConfirmation() throws Exception {
		location(4.676389, -74.048611);
		photo();
		assertThat(messaging.sent()).extracting(Sent::kind).contains("image", "buttons");

		text("sí, es correcto");
		assertThat(replies().getLast()).contains("quedó registrado");
	}

	@Test
	void sayingNoDiscardsTheReport() throws Exception {
		photo();
		location(4.676389, -74.048611);

		button(MessagingPort.CONFIRM_NO);

		assertThat(replies().getLast()).contains("descarté");
		mvc.perform(get("/api/issues")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unclearAnswerAsksAgain() throws Exception {
		photo();
		location(4.676389, -74.048611);
		messaging.clear();

		text("mmm no sé");

		assertThat(messaging.sent()).singleElement().extracting(Sent::kind).isEqualTo("buttons");
	}

	@Test
	void redeliveredMessagesAreHandledOnce() throws Exception {
		String payload = envelope(message("text", "\"text\":{\"body\":\"Hola\"}"));

		deliver(payload);
		deliver(payload);
		deliver(payload);

		assertThat(messaging.sent()).hasSize(1);
	}

	@Test
	void locationOutsideBogotaIsRefused() throws Exception {
		location(6.2442, -75.5812);

		assertThat(replies().getLast()).contains("fuera de Bogotá");
	}

	@Test
	void unsupportedMessagesGetAHint() throws Exception {
		deliver(envelope(message("audio", "\"audio\":{\"id\":\"a1\",\"mime_type\":\"audio/ogg\"}")));

		assertThat(replies().getLast()).contains("fotos, texto y ubicación");
	}

	@Test
	void phoneNumbersAreNeverStored() throws Exception {
		photo();
		location(4.676389, -74.048611);
		button(MessagingPort.CONFIRM_YES);

		for (String table : new String[] {"report", "processed_message", "issue", "status_transition", "media"}) {
			String dump = jdbc.sql("SELECT coalesce(string_agg(t::text, '|'), '') FROM " + table + " t")
					.query(String.class).single();
			assertThat(dump).as(table).doesNotContain(CITIZEN);
		}
	}

	@Test
	void deliveryStatusesAreAcceptedAndIgnored() throws Exception {
		String statusPayload = """
				{"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
				 "messaging_product":"whatsapp","metadata":{"phone_number_id":"PHONE_ID"},
				 "statuses":[{"id":"wamid.X","status":"failed","recipient_id":"573001112233",
				   "errors":[{"code":131026,"title":"Message undeliverable","error_data":{"details":"x"}}]}]}}]}]}
				""";
		deliver(statusPayload).andExpect(status().isOk());
		assertThat(messaging.sent()).isEmpty();
	}
}
