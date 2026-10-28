package com.civicrepairs.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the account API in tests: registers a citizen, confirms the email and returns the session cookie. */
public final class WebAccounts {

	public static final String SESSION_COOKIE = "civic_session";

	private WebAccounts() {
	}

	public static Cookie register(MockMvc mvc, String email, String displayName) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"displayName\":\"%s\",\"password\":\"una clave larga\"}"
								.formatted(email, displayName)))
				.andExpect(status().isOk())
				.andReturn();
		Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
		if (cookie == null) {
			throw new AssertionError("no session cookie after register");
		}
		return cookie;
	}

	public static Cookie registerVerified(MockMvc mvc, InMemoryEmail email, String address, String displayName)
			throws Exception {
		Cookie cookie = register(mvc, address, displayName);
		mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"" + email.lastToken(address, "verificar") + "\"}"))
				.andExpect(status().isOk());
		return cookie;
	}
}
