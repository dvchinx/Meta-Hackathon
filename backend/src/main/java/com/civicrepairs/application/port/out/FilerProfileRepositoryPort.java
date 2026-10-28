package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.petition.FilerProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored encrypted. Implementations throw {@link PersonalDataUnavailableException} when they cannot encrypt or
 * decrypt (no key configured, or a different key).
 */
public interface FilerProfileRepositoryPort {

	Optional<FilerProfile> find(UUID userId);

	/** @param policyVersion data policy the citizen accepted when saving */
	void save(UUID userId, FilerProfile profile, String policyVersion, Instant at);

	void delete(UUID userId);
}
