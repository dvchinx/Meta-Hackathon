package com.civicrepairs.support;

import com.civicrepairs.application.port.out.GoogleIdentityPort;

import java.util.Optional;

/**
 * Fake of the Google port. Accepts tokens shaped {@code google:<sub>:<email>:<verified>:<name>}; anything else is
 * rejected like a forged token.
 */
public class FakeGoogleIdentity implements GoogleIdentityPort {

	public static String token(String subject, String email, boolean verified, String name) {
		return "google:" + subject + ":" + email + ":" + verified + ":" + name;
	}

	@Override
	public Optional<GoogleIdentity> verify(String idToken) {
		String[] parts = idToken.split(":", 5);
		if (parts.length != 5 || !parts[0].equals("google")) {
			return Optional.empty();
		}
		return Optional.of(new GoogleIdentity(parts[1], parts[2], Boolean.parseBoolean(parts[3]), parts[4]));
	}
}
