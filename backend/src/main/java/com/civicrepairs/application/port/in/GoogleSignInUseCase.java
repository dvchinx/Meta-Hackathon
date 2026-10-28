package com.civicrepairs.application.port.in;

/** Signs in (or up) with a Google ID token. */
public interface GoogleSignInUseCase {

	/** @return the session secret to put in the cookie */
	String signIn(String idToken);
}
