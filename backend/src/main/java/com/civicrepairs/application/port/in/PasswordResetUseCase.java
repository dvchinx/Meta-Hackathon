package com.civicrepairs.application.port.in;

public interface PasswordResetUseCase {

	/** Always succeeds from the caller's point of view, so addresses cannot be probed. */
	void request(String email);

	void reset(String token, String newPassword);
}
