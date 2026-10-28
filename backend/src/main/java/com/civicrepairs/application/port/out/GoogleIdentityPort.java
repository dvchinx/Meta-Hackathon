package com.civicrepairs.application.port.out;

import java.util.Optional;

/** Verifies a "Sign in with Google" ID token. */
public interface GoogleIdentityPort {

	/** Empty when the token is forged, expired, for another app, or Google sign-in is not configured. */
	Optional<GoogleIdentity> verify(String idToken);

	/** @param subject Google's stable account id ("sub") */
	record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
	}
}
