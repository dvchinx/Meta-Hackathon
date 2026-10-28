package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.petition.FilerProfile;

import java.util.Optional;
import java.util.UUID;

/** The citizen's "Datos para radicar". Optional, encrypted, deletable at any time. */
public interface ManageFilerProfileUseCase {

	Optional<FilerProfile> find(UUID userId);

	/**
	 * @param policyVersion the data policy version the citizen accepted in the form
	 * @throws PetitionException INVALID_PROFILE with a message for the first invalid field, or POLICY_OUTDATED
	 */
	FilerProfile save(UUID userId, ProfileInput input, String policyVersion);

	void delete(UUID userId);

	/** Raw form input; validated and normalized by {@link FilerProfile}. */
	record ProfileInput(String idType, String idNumber, String firstNames, String lastNames, String mobilePhone,
			String address) {
	}
}
