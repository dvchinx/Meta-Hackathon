package com.civicrepairs.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Browser sessions. Implementations store only the hash of the secret. */
public interface SessionRepositoryPort {

	void create(String tokenHash, UUID userId, Instant expiresAt);

	/** Touches the session and returns its user, or empty when unknown or expired. */
	Optional<UUID> findUser(String tokenHash, Instant now);

	void delete(String tokenHash);

	/** Signs the user out everywhere (e.g. after a password reset). */
	void deleteAllForUser(UUID userId);
}
