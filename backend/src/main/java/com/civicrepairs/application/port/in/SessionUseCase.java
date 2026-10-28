package com.civicrepairs.application.port.in;

import java.util.Optional;

public interface SessionUseCase {

	/** @return the session secret to put in the cookie */
	String login(String email, String password);

	void logout(String sessionSecret);

	Optional<AuthenticatedUser> resolve(String sessionSecret);
}
