package com.civicrepairs.adapters.in.web;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.application.port.in.PrivacyPolicy;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.application.port.out.VisionPort;
import com.civicrepairs.application.service.IssueNotifier;
import com.civicrepairs.domain.issue.AgencyResponse;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.RepairVerdict;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import com.civicrepairs.support.Database;
import com.civicrepairs.support.InMemoryEmail;
import com.civicrepairs.support.WebAccounts;
import jakarta.servlet.http.Cookie;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole life of a damage: report → petition → agency reply (admin panel) → notices → photo verification → public
 * history. Model ports are stubs (no Meta credits): the "after" photo of the repaired place has no damage in it.
 */
@Import({TestcontainersConfiguration.class, RepairLifecycleFlowTest.Stubs.class})
@SpringBootTest(properties = {"META_API_ENABLED=false", "VISION_SERVICE_URL=", "RESEND_API_KEY=",
		"PERSONAL_DATA_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "ADMIN_TOKEN=admin-token-for-tests",
		"NOTIFICATIONS_POLL_INTERVAL=PT1H", "PUBLIC_BASE_URL=https://civic.example"})
class RepairLifecycleFlowTest {

	private static final String ADMIN = "admin-token-for-tests";
	private static final byte[] REPAIRED_PLACE = "repaired".getBytes(StandardCharsets.UTF_8);

	@TestConfiguration
	static class Stubs {
		@Bean
		@Primary
		InMemoryEmail inMemoryEmail() {
			return new InMemoryEmail();
		}

		@Bean
		@Primary
		VisionPort stubVision() {
			return (photo, type) -> {
				// The repaired place shows no damage; any other photo shows 15% of the frame damaged.
				boolean repaired = new String(photo.data(), StandardCharsets.UTF_8).startsWith("repaired");
				return new VisionPort.VisionResult(repaired ? 0.0 : 0.15, repaired ? 0 : 1, 0, 0, jpeg(), jpeg());
			};
		}

		@Bean
		@Primary
		LanguageModelPort stubMuse() {
			return new LanguageModelPort() {
				@Override
				public Interpretation interpret(MediaContent photo, String context) {
					return new Interpretation(DamageType.POTHOLE, Urgency.MEDIUM, true, List.of(), "Hueco en la vía.",
							0.9, AssetOwner.UNKNOWN);
				}

				@Override
				public SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate) {
					return new SameDamageVerdict(false, 0.9);
				}

				@Override
				public AgencyResponse.Reading classifyAgencyResponse(String body) {
					return new AgencyResponse.Reading(AgencyResponseClassification.RESOLVES,
							"La entidad dice que tapó el hueco.", 0.9);
				}

				@Override
				public RepairVerdict judgeRepair(MediaContent before, MediaContent after, DamageType damageType) {
					return new RepairVerdict(RepairVerdict.Judgement.REPAIRED, 0.95, "El hueco aparece tapado.");
				}
			};
		}

		/** The blurred and marked copies only have to be valid images. */
		private static byte[] jpeg() {
			try {
				var out = new ByteArrayOutputStream();
				ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", out);
				return out.toByteArray();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@TempDir
	static Path mediaDir;

	@DynamicPropertySource
	static void mediaPath(DynamicPropertyRegistry registry) {
		registry.add("civic.media.path", () -> mediaDir.toString());
	}

	@Autowired
	WebApplicationContext context;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	JsonMapper json;
	@Autowired
	InMemoryEmail email;
	@Autowired
	IssueNotifier notifier;

	MockMvc mvc;
	Cookie reporter;
	Cookie filer;
	String issueId;

	@BeforeEach
	void setUp() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		Database.clean(jdbc);
		reporter = WebAccounts.registerVerified(mvc, email, "reporta@example.com", "Reporta");
		filer = WebAccounts.registerVerified(mvc, email, "radica@example.com", "Radica");

		var photo = new MockMultipartFile("photo", "hueco.jpg", "image/jpeg", "pothole".getBytes(StandardCharsets.UTF_8));
		JsonNode analysis = body(mvc.perform(multipart("/api/reports").file(photo)
				.param("latitude", "4.676389").param("longitude", "-74.048611").cookie(reporter)));
		issueId = body(mvc.perform(post("/api/reports/" + analysis.path("reportId").asString() + "/confirmation")
				.cookie(reporter).contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}")))
				.path("issueId").asString();
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private void filePetition() throws Exception {
		mvc.perform(put("/api/me/filer-profile").cookie(filer).contentType(MediaType.APPLICATION_JSON).content("""
						{"profile":{"idType":"CC","idNumber":"1020304050","firstNames":"Ana","lastNames":"Ruiz",
						 "mobilePhone":"3001234567","address":null},"acceptedPolicyVersion":"%s"}
						""".formatted(PrivacyPolicy.CURRENT_VERSION)))
				.andExpect(status().isOk());
		mvc.perform(post("/api/issues/" + issueId + "/petitions").cookie(filer).contentType(MediaType.APPLICATION_JSON)
						.content("{\"acceptedPolicyVersion\":\"" + PrivacyPolicy.CURRENT_VERSION + "\"}"))
				.andExpect(status().isOk());
	}

	private ResultActions agencyReplies(String token) throws Exception {
		var request = post("/api/admin/issues/" + issueId + "/agency-responses")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Le informamos que la cuadrilla tapó el hueco el 2 de noviembre.\"}");
		return mvc.perform(token == null ? request : request.header(AdminTokenInterceptor.HEADER, token));
	}

	private ResultActions verify(Cookie citizen, byte[] photo) throws Exception {
		return mvc.perform(multipart("/api/issues/" + issueId + "/verifications")
				.file(new MockMultipartFile("photo", "ahora.jpg", "image/jpeg", photo))
				.cookie(citizen));
	}

	private List<String> subjectsTo(String address) {
		return email.sent().stream().filter(m -> m.to().equals(address)).map(InMemoryEmail.Sent::subject).toList();
	}

	@Test
	void fromReportToVerifiedRepair() throws Exception {
		filePetition();
		notifier.dispatchPending();

		agencyReplies(null).andExpect(status().isUnauthorized());
		agencyReplies(ADMIN)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.classification").value("RESOLVES"))
				// The agency's word is a claim, never a repair.
				.andExpect(jsonPath("$.status").value("REPAIR_CLAIMED"));

		notifier.dispatchPending();
		assertThat(subjectsTo("reporta@example.com"))
				.contains("Se radicó la petición: Hueco en Chapinero", "¿Repararon el daño? Hueco en Chapinero")
				// AGENCY_RESPONDED was immediately followed by REPAIR_CLAIMED: only the latter is told.
				.noneMatch(subject -> subject.contains("respondió"));
		assertThat(subjectsTo("radica@example.com")).contains("¿Repararon el daño? Hueco en Chapinero");

		verify(reporter, REPAIRED_PLACE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("REPAIRED"))
				.andExpect(jsonPath("$.status").value("VERIFIED"))
				.andExpect(jsonPath("$.reason").value("El hueco aparece tapado."));

		mvc.perform(get("/api/issues/" + issueId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps[*].to").value(org.hamcrest.Matchers.contains(
						"REPORTED", "FILED", "AGENCY_RESPONDED", "REPAIR_CLAIMED", "VERIFIED")))
				.andExpect(jsonPath("$.agencyResponses[0].classification").value("RESOLVES"))
				.andExpect(jsonPath("$.verifications[0].outcome").value("REPAIRED"))
				.andExpect(jsonPath("$.verifications[0].photoUrl").isString());

		notifier.dispatchPending();
		assertThat(subjectsTo("reporta@example.com")).contains("Reparación verificada: Hueco en Chapinero");
		verify(filer, REPAIRED_PLACE).andExpect(status().isConflict());

		mvc.perform(get("/api/admin/overview").header(AdminTokenInterceptor.HEADER, ADMIN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issuesByStatus.VERIFIED").value(1))
				.andExpect(jsonPath("$.metaEnabled").value(false));
	}

	@Test
	void aPhotoThatStillShowsTheDamageReopensTheIssue() throws Exception {
		filePetition();
		agencyReplies(ADMIN).andExpect(status().isOk());

		// Muse (stub) says repaired, but SAM still finds the damage: the signals disagree.
		verify(filer, "pothole".getBytes(StandardCharsets.UTF_8))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("INCONCLUSIVE"))
				.andExpect(jsonPath("$.status").value("NEEDS_MORE_EVIDENCE"));
		// Another photo can still settle it.
		verify(filer, REPAIRED_PLACE).andExpect(jsonPath("$.status").value("VERIFIED"));
	}

	@Test
	void photosAreOnlyAskedForOnceTheAgencyClaimsTheRepair() throws Exception {
		verify(reporter, REPAIRED_PLACE)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WRONG_STATUS"));

		agencyReplies(ADMIN).andExpect(status().isConflict());
	}

	@Test
	void historyNeverShowsWhoDidWhat() throws Exception {
		filePetition();
		agencyReplies(ADMIN);
		verify(filer, REPAIRED_PLACE);

		String history = mvc.perform(get("/api/issues/" + issueId + "/history"))
				.andReturn().getResponse().getContentAsString();
		String verifier = jdbc.sql("SELECT verifier_id FROM verification").query(String.class).single();
		assertThat(history).doesNotContain(verifier).doesNotContain("radica@example.com").doesNotContain("1020304050");
	}
}
