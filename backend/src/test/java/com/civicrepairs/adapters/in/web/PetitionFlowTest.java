package com.civicrepairs.adapters.in.web;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.application.port.in.PrivacyPolicy;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Simulated filing with the citizen's own data, end to end. Model fakes, real PostGIS, no Meta credits. */
@Import({TestcontainersConfiguration.class, PetitionFlowTest.Fakes.class})
@SpringBootTest(properties = {"META_API_ENABLED=false", "VISION_SERVICE_URL=", "RESEND_API_KEY=",
		"PERSONAL_DATA_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class PetitionFlowTest {

	@TestConfiguration
	static class Fakes {
		@Bean
		@Primary
		InMemoryEmail inMemoryEmail() {
			return new InMemoryEmail();
		}
	}

	private static final String PROFILE = """
			{"idType":"CC","idNumber":"1.020.304.050","firstNames":"Ana María","lastNames":"Ruiz",
			 "mobilePhone":"300 123 4567","address":null}
			""";

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
	Cookie ana;
	Cookie beto;
	String issueId;

	@BeforeEach
	void setUp() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		for (String table : new String[] {"petition", "filer_profile", "issue_endorsement", "report",
				"status_transition", "issue", "model_call", "media", "email_token", "user_session", "user_account"}) {
			jdbc.sql("DELETE FROM " + table).update();
		}
		ana = WebAccounts.registerVerified(mvc, email, "ana@example.com", "Ana");
		beto = WebAccounts.registerVerified(mvc, email, "beto@example.com", "Beto");
		issueId = reportedIssue(ana);
	}

	private String reportedIssue(Cookie citizen) throws Exception {
		var photo = new MockMultipartFile("photo", "hueco.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 9});
		JsonNode analysis = body(mvc.perform(multipart("/api/reports").file(photo)
				.param("latitude", "4.676389").param("longitude", "-74.048611").cookie(citizen)));
		JsonNode confirmation = body(mvc.perform(post("/api/reports/" + analysis.path("reportId").asString()
				+ "/confirmation").cookie(citizen).contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}")));
		return confirmation.path("issueId").asString();
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private ResultActions saveProfile(Cookie citizen, String profile, String policyVersion) throws Exception {
		return mvc.perform(put("/api/me/filer-profile").cookie(citizen).contentType(MediaType.APPLICATION_JSON)
				.content("{\"profile\":" + profile + ",\"acceptedPolicyVersion\":\"" + policyVersion + "\"}"));
	}

	private ResultActions file(Cookie citizen) throws Exception {
		return mvc.perform(post("/api/issues/" + issueId + "/petitions").cookie(citizen)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"acceptedPolicyVersion\":\"" + PrivacyPolicy.CURRENT_VERSION + "\"}"));
	}

	@Test
	void theIssueShowsWhoIsResponsibleAndWhereToReportForReal() throws Exception {
		mvc.perform(get("/api/issues/" + issueId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REPORTED"))
				.andExpect(jsonPath("$.agency").value("Unidad de Mantenimiento Vial"))
				.andExpect(jsonPath("$.locality").value("Chapinero"))
				.andExpect(jsonPath("$.officialChannel.url").value("https://bogota.gov.co/yo-participo/reporta-sdqs/huecos"))
				.andExpect(jsonPath("$.petition").value(nullValue()));
	}

	@Test
	void filingNeedsTheProfileAndTheCurrentPolicy() throws Exception {
		file(ana).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));

		saveProfile(ana, PROFILE, "2020-01-01")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POLICY_OUTDATED"));
		saveProfile(ana, PROFILE.replace("300 123 4567", "601 123 4567"), PrivacyPolicy.CURRENT_VERSION)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PROFILE"))
				.andExpect(jsonPath("$.message").value(startsWith("Escribe un celular colombiano")));
	}

	@Test
	void theProfileIsNormalizedAndStoredEncrypted() throws Exception {
		saveProfile(ana, PROFILE, PrivacyPolicy.CURRENT_VERSION)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile.idNumber").value("1020304050"))
				.andExpect(jsonPath("$.profile.mobilePhone").value("3001234567"));

		mvc.perform(get("/api/me/filer-profile").cookie(ana)).andExpect(jsonPath("$.profile.firstNames").value("Ana María"));
		mvc.perform(get("/api/me/filer-profile").cookie(beto)).andExpect(jsonPath("$.profile").value(nullValue()));

		byte[] stored = jdbc.sql("SELECT data FROM filer_profile").query(byte[].class).single();
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("1020304050").doesNotContain("Ruiz");
		assertThat(jdbc.sql("SELECT consent_policy_version FROM filer_profile").query(String.class).single())
				.isEqualTo(PrivacyPolicy.CURRENT_VERSION);
	}

	@Test
	void filingMovesTheIssueToFiledAndOnlyTheFilerSeesTheirData() throws Exception {
		saveProfile(ana, PROFILE, PrivacyPolicy.CURRENT_VERSION).andExpect(status().isOk());

		JsonNode receipt = body(file(ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.radicado").value(startsWith("SIM-")))
				.andExpect(jsonPath("$.agency").value("Unidad de Mantenimiento Vial")));
		String petitionId = receipt.path("petitionId").asString();

		String publicIssues = mvc.perform(get("/api/issues"))
				.andExpect(jsonPath("$[0].status").value("FILED"))
				.andExpect(jsonPath("$[0].petition.radicado").value(receipt.path("radicado").asString()))
				.andExpect(jsonPath("$[0].petition.businessDaysLeft").isNumber())
				.andReturn().getResponse().getContentAsString();
		assertThat(publicIssues).doesNotContain("1020304050").doesNotContain("ana@example.com").doesNotContain("Ruiz");

		mvc.perform(get("/api/petitions/" + petitionId).cookie(ana))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.petitioner.idNumber").value("1020304050"))
				.andExpect(jsonPath("$.petitioner.fullName").value("Ana María Ruiz"))
				.andExpect(jsonPath("$.petitioner.email").value("ana@example.com"))
				.andExpect(jsonPath("$.locality").value("Chapinero"))
				.andExpect(jsonPath("$.subject").isString());
		mvc.perform(get("/api/petitions/" + petitionId).cookie(beto)).andExpect(status().isNotFound());

		mvc.perform(get("/api/me/reports").cookie(ana))
				.andExpect(jsonPath("$[0].myPetitionId").value(petitionId))
				.andExpect(jsonPath("$[0].petition.radicado").value(receipt.path("radicado").asString()));
	}

	@Test
	void anIssueIsFiledOnlyOnce() throws Exception {
		saveProfile(ana, PROFILE, PrivacyPolicy.CURRENT_VERSION);
		saveProfile(beto, PROFILE.replace("1.020.304.050", "79123456"), PrivacyPolicy.CURRENT_VERSION);

		file(ana).andExpect(status().isOk());
		file(beto).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_FILED"));
	}

	@Test
	void deletingTheProfileKeepsTheFiledPetition() throws Exception {
		saveProfile(ana, PROFILE, PrivacyPolicy.CURRENT_VERSION);
		String petitionId = body(file(ana)).path("petitionId").asString();

		mvc.perform(delete("/api/me/filer-profile").cookie(ana)).andExpect(status().isNoContent());

		mvc.perform(get("/api/me/filer-profile").cookie(ana)).andExpect(jsonPath("$.profile").value(nullValue()));
		mvc.perform(get("/api/petitions/" + petitionId).cookie(ana))
				.andExpect(jsonPath("$.petitioner.idNumber").value("1020304050"));
	}

	@Test
	void unverifiedAccountsCannotFile() throws Exception {
		Cookie unverified = WebAccounts.register(mvc, "nuevo@example.com", "Nuevo");

		file(unverified).andExpect(status().isForbidden());
	}
}
