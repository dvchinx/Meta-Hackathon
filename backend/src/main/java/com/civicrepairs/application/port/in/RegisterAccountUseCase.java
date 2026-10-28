package com.civicrepairs.application.port.in;

/** Creates the account, signs the citizen in and sends the verification link. */
public interface RegisterAccountUseCase {

	/** @return the session secret to put in the cookie */
	String register(String email, String displayName, String password);
}
