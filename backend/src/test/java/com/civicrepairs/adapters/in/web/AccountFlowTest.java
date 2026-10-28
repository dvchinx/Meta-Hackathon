package com.civicrepairs.adapters.in.web;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.support.FakeGoogleIdentity;
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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accounts end to end through the HTTP API, with captured emails and a real PostGIS. */
@Import({TestcontainersConfiguration.class, AccountFlowTest.Fakes.class})
@SpringBootTest(properties = {"META_API_ENABLED=false", "VISION_SERVICE_URL=", "RESEND_API_KEY=",
		"PUBLIC_BASE_URL=https://civic.example", "GOOGLE_CLIENT_ID=test-client.apps.googleusercontent.com"})
class AccountFlowTest {

	private static final String ANA = "ana@example.com";
	private static final double LAT = 4.676389;
	private static final double LON = -74.048611;

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
		InMemoryEmail inMemoryEmail() {
			return new InMemoryEmail();
		}

		@Bean
		@Primary
		FakeGoogleIdentity fakeGoogleIdentity() {
			return new FakeGoogleIdentity();
		}
	}

	@Autowired
	WebApplicationContext context;
	@Autowired
	InMemoryEmail email;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	JsonMapper json;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		email.clear();
		Database.clean(jdbc);
	}

	private ResultActions postJson(String path, String body, Cookie... cookies) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		return mvc.perform(request);
	}

	private ResultActions report(Cookie session) throws Exception {
		var photo = new MockMultipartFile("photo", "hueco.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1});
		return mvc.perform(multipart("/api/reports").file(photo).param("latitude", String.valueOf(LAT))
				.param("longitude", String.valueOf(LON)).cookie(session));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	@Test
	void registerVerifyReportAndSeeMyReports() throws Exception {
		Cookie session = WebAccounts.register(mvc, "Ana@Example.com ", "  Ana   María ");

		mvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(jsonPath("$.user.email").value(ANA))
				.andExpect(jsonPath("$.user.displayName").value("Ana María"))
				.andExpect(jsonPath("$.user.emailVerified").value(false));

		// Unverified: can look around, cannot report.
		report(session).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		assertThat(email.sent()).singleElement().satisfies(m -> {
			assertThat(m.to()).isEqualTo(ANA);
			assertThat(m.text()).contains("https://civic.example/#/verificar?token=");
		});
		String token = email.lastToken(ANA, "verificar");
		postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.emailVerified").value(true));
		// Links are single use.
		postJson("/api/auth/verify-email", "{\"token\":\"" + token + "\"}").andExpect(status().isUnprocessableContent());

		JsonNode analysis = body(report(session).andExpect(status().isOk()));
		postJson("/api/reports/" + analysis.path("reportId").asString() + "/confirmation", "{\"accepted\":true}", session)
				.andExpect(status().isOk());

		mvc.perform(get("/api/me/reports").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reportStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$[0].damageType").value("POTHOLE"))
				.andExpect(jsonPath("$[0].issueStatus").value("REPORTED"))
				.andExpect(jsonPath("$[0].issueReportCount").value(1))
				.andExpect(jsonPath("$[0].photoUrl").value(org.hamcrest.Matchers.startsWith("/media/")));
	}

	@Test
	void anonymousVisitorsSeeTheMapButCannotReport() throws Exception {
		mvc.perform(get("/api/issues")).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.user").value(nullValue()));
		mvc.perform(get("/api/me/reports")).andExpect(status().isUnauthorized());
		var photo = new MockMultipartFile("photo", "h.jpg", "image/jpeg", new byte[] {1});
		mvc.perform(multipart("/api/reports").file(photo).param("latitude", "4.6").param("longitude", "-74.08"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void loginLogoutAndWrongPassword() throws Exception {
		WebAccounts.register(mvc, ANA, "Ana");

		postJson("/api/auth/login", "{\"email\":\"ANA@example.com\",\"password\":\"una clave larga\"}")
				.andExpect(status().isOk())
				.andExpect(cookie().httpOnly(WebAccounts.SESSION_COOKIE, true))
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")));
		postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"otra clave\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
		postJson("/api/auth/login", "{\"email\":\"nadie@example.com\",\"password\":\"una clave larga\"}")
				.andExpect(status().isUnauthorized());

		Cookie session = postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"una clave larga\"}")
				.andReturn().getResponse().getCookie(WebAccounts.SESSION_COOKIE);
		postJson("/api/auth/logout", "", session).andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(WebAccounts.SESSION_COOKIE, 0));
		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.user").value(nullValue()));
	}

	@Test
	void registrationValidation() throws Exception {
		WebAccounts.register(mvc, ANA, "Ana");

		postJson("/api/auth/register", "{\"email\":\"ana@example.com\",\"displayName\":\"Otra\",\"password\":\"una clave larga\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
		postJson("/api/auth/register", "{\"email\":\"no-es-correo\",\"displayName\":\"Ana\",\"password\":\"una clave larga\"}")
				.andExpect(status().isBadRequest());
		postJson("/api/auth/register", "{\"email\":\"b@example.com\",\"displayName\":\"A\",\"password\":\"una clave larga\"}")
				.andExpect(status().isBadRequest());
		postJson("/api/auth/register", "{\"email\":\"b@example.com\",\"displayName\":\"Beto\",\"password\":\"corta\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void passwordResetSignsOutEverywhereAndVerifiesTheEmail() throws Exception {
		Cookie session = WebAccounts.register(mvc, ANA, "Ana");

		postJson("/api/auth/password-reset", "{\"email\":\"ana@example.com\"}").andExpect(status().isNoContent());
		postJson("/api/auth/password-reset", "{\"email\":\"nadie@example.com\"}").andExpect(status().isNoContent());
		String token = email.lastToken(ANA, "restablecer");

		postJson("/api/auth/password-reset/confirm", "{\"token\":\"" + token + "\",\"password\":\"clave nueva larga\"}")
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.user").value(nullValue()));
		postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"una clave larga\"}")
				.andExpect(status().isUnauthorized());
		postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"clave nueva larga\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.emailVerified").value(true));
	}

	@Test
	void iSawItTooCountsEachPersonOnce() throws Exception {
		Cookie ana = WebAccounts.registerVerified(mvc, email, ANA, "Ana");
		Cookie beto = WebAccounts.registerVerified(mvc, email, "beto@example.com", "Beto");
		JsonNode analysis = body(report(ana));
		String issueId = body(postJson("/api/reports/" + analysis.path("reportId").asString() + "/confirmation",
				"{\"accepted\":true}", ana)).path("issueId").asString();

		postJson("/api/issues/" + issueId + "/endorsements", "", beto)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counted").value(true))
				.andExpect(jsonPath("$.reportCount").value(2));
		postJson("/api/issues/" + issueId + "/endorsements", "", beto)
				.andExpect(jsonPath("$.counted").value(false))
				.andExpect(jsonPath("$.reportCount").value(2));
		// The reporter is already counted through the report itself.
		postJson("/api/issues/" + issueId + "/endorsements", "", ana).andExpect(jsonPath("$.counted").value(false));
		postJson("/api/issues/" + issueId + "/endorsements", "").andExpect(status().isUnauthorized());

		mvc.perform(get("/api/issues")).andExpect(jsonPath("$[0].reportCount").value(2));
	}

	private ResultActions google(String token) throws Exception {
		return postJson("/api/auth/google", "{\"credential\":\"" + token + "\"}");
	}

	@Test
	void googleSignUpCreatesAVerifiedAccountThatCanReport() throws Exception {
		Cookie session = google(FakeGoogleIdentity.token("g-1", "Ana@Gmail.com", true, "Ana María"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.email").value("ana@gmail.com"))
				.andExpect(jsonPath("$.user.displayName").value("Ana María"))
				.andExpect(jsonPath("$.user.emailVerified").value(true))
				.andReturn().getResponse().getCookie(WebAccounts.SESSION_COOKIE);

		report(session).andExpect(status().isOk());
		assertThat(email.sent()).isEmpty();

		// Second sign-in finds the same account by Google id, even if the Gmail address changed.
		google(FakeGoogleIdentity.token("g-1", "ana.nueva@gmail.com", true, "Ana María"))
				.andExpect(jsonPath("$.user.email").value("ana@gmail.com"));
		assertThat(jdbc.sql("SELECT count(*) FROM user_account").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void googleLinksAnExistingVerifiedAccountAndKeepsItsPassword() throws Exception {
		WebAccounts.registerVerified(mvc, email, ANA, "Ana");
		String id = jdbc.sql("SELECT id::text FROM user_account").query(String.class).single();

		google(FakeGoogleIdentity.token("g-ana", ANA, true, "Ana Google"))
				.andExpect(jsonPath("$.user.id").value(id))
				.andExpect(jsonPath("$.user.displayName").value("Ana"));
		postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"una clave larga\"}")
				.andExpect(status().isOk());
	}

	@Test
	void googleTakesBackAnAddressSomeoneElseRegisteredWithoutVerifying() throws Exception {
		Cookie impostor = WebAccounts.register(mvc, ANA, "Impostor");

		google(FakeGoogleIdentity.token("g-ana", ANA, true, "Ana")).andExpect(status().isOk())
				.andExpect(jsonPath("$.user.emailVerified").value(true));

		mvc.perform(get("/api/auth/me").cookie(impostor)).andExpect(jsonPath("$.user").value(nullValue()));
		postJson("/api/auth/login", "{\"email\":\"ana@example.com\",\"password\":\"una clave larga\"}")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void googleRejectsForgedTokensAndUnverifiedAddresses() throws Exception {
		google("forged").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		google(FakeGoogleIdentity.token("g-2", "x@gmail.com", false, "X")).andExpect(status().isUnprocessableContent());
		postJson("/api/auth/google", "{}").andExpect(status().isUnprocessableContent());
		assertThat(jdbc.sql("SELECT count(*) FROM user_account").query(Integer.class).single()).isZero();
	}

	@Test
	void googleOnlyAccountsHaveNoPasswordToGuess() throws Exception {
		google(FakeGoogleIdentity.token("g-3", "solo@gmail.com", true, "Solo")).andExpect(status().isOk());

		postJson("/api/auth/login", "{\"email\":\"solo@gmail.com\",\"password\":\"cualquier cosa\"}")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void configExposesOnlyThePublicClientId() throws Exception {
		mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.googleClientId").exists());
	}

	@Test
	void nothingPersonalLeaksThroughPublicEndpoints() throws Exception {
		Cookie ana = WebAccounts.registerVerified(mvc, email, ANA, "Ana María");
		JsonNode analysis = body(report(ana));
		postJson("/api/reports/" + analysis.path("reportId").asString() + "/confirmation", "{\"accepted\":true}", ana);

		String issues = mvc.perform(get("/api/issues")).andReturn().getResponse().getContentAsString();
		assertThat(issues).doesNotContain("ana@").doesNotContain("Ana María");
		String reporterId = jdbc.sql("SELECT reporter_id FROM report").query(String.class).single();
		String userId = jdbc.sql("SELECT id::text FROM user_account").query(String.class).single();
		assertThat(reporterId).doesNotContain(userId);
		assertThat(jdbc.sql("SELECT token_hash FROM user_session").query(String.class).list())
				.allMatch(h -> h.matches("[0-9a-f]{64}"));
	}
}
