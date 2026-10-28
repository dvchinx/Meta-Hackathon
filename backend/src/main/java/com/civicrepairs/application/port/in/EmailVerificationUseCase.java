package com.civicrepairs.application.port.in;

import java.util.UUID;

public interface EmailVerificationUseCase {

	/** @return the verified user */
	AuthenticatedUser verify(String token);

	void resend(UUID userId);
}
