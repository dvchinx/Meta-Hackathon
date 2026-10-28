package com.civicrepairs.adapters.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The session cookie: HttpOnly (scripts never see it), SameSite=Lax (cross-site POSTs do not carry it, which is
 * our CSRF protection), Secure whenever the request came over HTTPS.
 */
final class SessionCookies {

	static final String NAME = "civic_session";
	private static final Duration MAX_AGE = Duration.ofDays(30);

	private SessionCookies() {
	}

	static Optional<String> read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies).filter(c -> NAME.equals(c.getName())).map(Cookie::getValue).findFirst();
	}

	static ResponseCookie create(String secret, HttpServletRequest request) {
		return builder(secret, request).maxAge(MAX_AGE).build();
	}

	static ResponseCookie clear(HttpServletRequest request) {
		return builder("", request).maxAge(0).build();
	}

	private static ResponseCookie.ResponseCookieBuilder builder(String value, HttpServletRequest request) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(request.isSecure())
				.sameSite("Lax")
				.path("/");
	}
}
