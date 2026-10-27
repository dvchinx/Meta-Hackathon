package com.civicrepairs.adapters.in.web;

import com.civicrepairs.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"META_API_ENABLED=false", "VISION_SERVICE_URL="})
class ReportFlowTest {

	private static final String TOKEN_A = "token-of-citizen-a-0001";
	private static final String TOKEN_B = "token-of-citizen-b-0002";
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

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		for (String table : new String[] {"report", "status_transition", "issue", "model_call", "media"}) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}

	private ResultActions report(String token, double lat, double lon) throws Exception {
		var photo = new MockMultipartFile("photo", "hueco.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2});
		return mvc.perform(multipart("/api/reports").file(photo)
				.param("latitude", String.valueOf(lat))
				.param("longitude", String.valueOf(lon))
				.param("text", "Hueco grande frente al parque")
				.header("X-Reporter-Token", token));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private ResultActions confirm(String token, String reportId, boolean accepted) throws Exception {
		return mvc.perform(post("/api/reports/" + reportId + "/confirmation")
				.header("X-Reporter-Token", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"accepted\":" + accepted + "}"));
	}

	@Test
	void reportConfirmAndDeduplicate() throws Exception {
		JsonNode first = body(report(TOKEN_A, LAT, LON)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("AWAITING_CONFIRMATION"))
				.andExpect(jsonPath("$.damageType").value("POTHOLE"))
				.andExpect(jsonPath("$.agency").value("Instituto de Desarrollo Urbano")));

		mvc.perform(get(first.path("markedPhotoUrl").asString()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"));

		JsonNode created = body(confirm(TOKEN_A, first.path("reportId").asString(), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newIssue").value(true))
				.andExpect(jsonPath("$.reportCount").value(1)));

		// A second citizen, 10 m away, reports the same damage.
		JsonNode second = body(report(TOKEN_B, LAT + 10 / 111_320.0, LON).andExpect(status().isOk()));
		confirm(TOKEN_B, second.path("reportId").asString(), true)
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
		JsonNode analysis = body(report(TOKEN_A, LAT, LON));
		confirm(TOKEN_A, analysis.path("reportId").asString(), true);

		UUID original = jdbc.sql("SELECT id FROM media WHERE visibility = 'PRIVATE'").query(UUID.class).single();
		mvc.perform(get("/media/" + original + ".jpg")).andExpect(status().isNotFound());
		mvc.perform(get("/media/" + UUID.randomUUID() + ".jpg")).andExpect(status().isNotFound());

		String issues = mvc.perform(get("/api/issues")).andReturn().getResponse().getContentAsString();
		String reporterHash = jdbc.sql("SELECT reporter_id FROM report").query(String.class).single();
		assertThat(issues).doesNotContain(reporterHash).doesNotContain(TOKEN_A).doesNotContain(original.toString());
		assertThat(reporterHash).doesNotContain(TOKEN_A);
	}

	@Test
	void onlyTheReporterCanConfirm() throws Exception {
		JsonNode analysis = body(report(TOKEN_A, LAT, LON));

		confirm(TOKEN_B, analysis.path("reportId").asString(), true).andExpect(status().isNotFound());
		confirm(TOKEN_A, analysis.path("reportId").asString(), true).andExpect(status().isOk());
	}

	@Test
	void rejectingTheSummaryCreatesNoIssue() throws Exception {
		JsonNode analysis = body(report(TOKEN_A, LAT, LON));

		confirm(TOKEN_A, analysis.path("reportId").asString(), false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accepted").value(false));

		mvc.perform(get("/api/issues")).andExpect(jsonPath("$.length()").value(0));
		confirm(TOKEN_A, analysis.path("reportId").asString(), true).andExpect(status().isConflict());
	}

	@Test
	void onlyBogota() throws Exception {
		report(TOKEN_A, 6.2442, -75.5812)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("OUTSIDE_BOGOTA"));
	}

	@Test
	void requiresAPhotoAndAToken() throws Exception {
		var notAPhoto = new MockMultipartFile("photo", "x.txt", "text/plain", "hola".getBytes());
		mvc.perform(multipart("/api/reports").file(notAPhoto).param("latitude", "4.6").param("longitude", "-74.08")
						.header("X-Reporter-Token", TOKEN_A))
				.andExpect(status().isUnsupportedMediaType());
		report("short", LAT, LON).andExpect(status().isBadRequest());
	}
}
