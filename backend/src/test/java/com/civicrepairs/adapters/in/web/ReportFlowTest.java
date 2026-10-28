package com.civicrepairs.adapters.in.web;

import com.civicrepairs.TestcontainersConfiguration;
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

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole web flow with the in-memory model fakes and a real PostGIS. No Meta credits involved. */
@Import({TestcontainersConfiguration.class, ReportFlowTest.Fakes.class})
@SpringBootTest(properties = {"META_API_ENABLED=false", "VISION_SERVICE_URL=", "RESEND_API_KEY="})
class ReportFlowTest {

	@TestConfiguration
	static class Fakes {
		@Bean
		@Primary
		InMemoryEmail inMemoryEmail() {
			return new InMemoryEmail();
		}
	}

	private static final double LAT = 4.676389;
	private static final double LON = -74.048611;

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

	MockMvc mvc;
	Cookie citizenA;
	Cookie citizenB;

	@BeforeEach
	void setUp() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		for (String table : new String[] {"petition", "filer_profile", "issue_endorsement", "report", "status_transition", "issue", "model_call",
				"media", "email_token", "user_session", "user_account"}) {
			jdbc.sql("DELETE FROM " + table).update();
		}
		citizenA = WebAccounts.registerVerified(mvc, email, "a@example.com", "Ciudadana A");
		citizenB = WebAccounts.registerVerified(mvc, email, "b@example.com", "Ciudadano B");
	}

	private ResultActions report(Cookie citizen, double lat, double lon) throws Exception {
		var photo = new MockMultipartFile("photo", "hueco.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2});
		return mvc.perform(multipart("/api/reports").file(photo)
				.param("latitude", String.valueOf(lat))
				.param("longitude", String.valueOf(lon))
				.param("text", "Hueco grande frente al parque")
				.cookie(citizen));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private ResultActions confirm(Cookie citizen, String reportId, boolean accepted) throws Exception {
		return mvc.perform(post("/api/reports/" + reportId + "/confirmation")
				.cookie(citizen)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"accepted\":" + accepted + "}"));
	}

	@Test
	void reportConfirmAndDeduplicate() throws Exception {
		JsonNode first = body(report(citizenA, LAT, LON)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("AWAITING_CONFIRMATION"))
				.andExpect(jsonPath("$.damageType").value("POTHOLE"))
				.andExpect(jsonPath("$.agency").value("Unidad de Mantenimiento Vial"))
				.andExpect(jsonPath("$.locality").value("Chapinero"))
				.andExpect(jsonPath("$.callEmergencyLine").value(false)));

		mvc.perform(get(first.path("markedPhotoUrl").asString()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"));

		JsonNode created = body(confirm(citizenA, first.path("reportId").asString(), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newIssue").value(true))
				.andExpect(jsonPath("$.reportCount").value(1)));

		// A second citizen, 10 m away, reports the same damage.
		JsonNode second = body(report(citizenB, LAT + 10 / 111_320.0, LON).andExpect(status().isOk()));
		confirm(citizenB, second.path("reportId").asString(), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newIssue").value(false))
				.andExpect(jsonPath("$.issueId").value(created.path("issueId").asString()))
				.andExpect(jsonPath("$.reportCount").value(2));

		mvc.perform(get("/api/issues"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reportCount").value(2))
				.andExpect(jsonPath("$[0].status").value("REPORTED"));
	}

	@Test
	void originalPhotosAndReporterDataNeverGoPublic() throws Exception {
		JsonNode analysis = body(report(citizenA, LAT, LON));
		confirm(citizenA, analysis.path("reportId").asString(), true);

		UUID original = jdbc.sql("SELECT id FROM media WHERE visibility = 'PRIVATE'").query(UUID.class).single();
		mvc.perform(get("/media/" + original + ".jpg")).andExpect(status().isNotFound());
		mvc.perform(get("/media/" + UUID.randomUUID() + ".jpg")).andExpect(status().isNotFound());

		String issues = mvc.perform(get("/api/issues")).andReturn().getResponse().getContentAsString();
		String reporterHash = jdbc.sql("SELECT reporter_id FROM report").query(String.class).single();
		assertThat(issues).doesNotContain(reporterHash).doesNotContain("a@example.com").doesNotContain(original.toString());
	}

	@Test
	void onlyTheReporterCanConfirm() throws Exception {
		JsonNode analysis = body(report(citizenA, LAT, LON));

		confirm(citizenB, analysis.path("reportId").asString(), true).andExpect(status().isNotFound());
		confirm(citizenA, analysis.path("reportId").asString(), true).andExpect(status().isOk());
	}

	@Test
	void rejectingTheSummaryCreatesNoIssue() throws Exception {
		JsonNode analysis = body(report(citizenA, LAT, LON));

		confirm(citizenA, analysis.path("reportId").asString(), false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accepted").value(false));

		mvc.perform(get("/api/issues")).andExpect(jsonPath("$.length()").value(0));
		confirm(citizenA, analysis.path("reportId").asString(), true).andExpect(status().isConflict());
	}

	@Test
	void onlyBogota() throws Exception {
		report(citizenA, 6.2442, -75.5812)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("OUTSIDE_BOGOTA"));
		// Soacha: inside the rough box around Bogotá, but outside its localities.
		report(citizenA, 4.5794, -74.2168)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("OUTSIDE_BOGOTA"));
	}

	@Test
	void requiresAPhoto() throws Exception {
		var notAPhoto = new MockMultipartFile("photo", "x.txt", "text/plain", "hola".getBytes());
		mvc.perform(multipart("/api/reports").file(notAPhoto).param("latitude", "4.6").param("longitude", "-74.08")
						.cookie(citizenA))
				.andExpect(status().isUnsupportedMediaType());
	}
}
