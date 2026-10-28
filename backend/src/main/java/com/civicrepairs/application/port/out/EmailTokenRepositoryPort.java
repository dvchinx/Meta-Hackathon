package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.account.EmailTokenPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Single-use links sent by email. Implementations store only the hash of the secret. */
public interface EmailTokenRepositoryPort {

	void store(String tokenHash, UUID userId, EmailTokenPurpose purpose, Instant expiresAt);

	/** Marks the token used and returns its user; empty when unknown, expired, already used or of another purpose. */
	Optional<UUID> consume(String tokenHash, EmailTokenPurpose purpose, Instant now);
}
