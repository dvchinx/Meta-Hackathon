package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.PetitionException;
import com.civicrepairs.application.port.in.PetitionException.Code;
import com.civicrepairs.application.port.in.PrivacyPolicy;

/** Consent only counts for the policy text the citizen actually saw. */
final class Policies {

	private Policies() {
	}

	static void requireCurrent(String acceptedVersion) {
		if (!PrivacyPolicy.CURRENT_VERSION.equals(acceptedVersion)) {
			throw new PetitionException(Code.POLICY_OUTDATED,
					"La política de tratamiento de datos cambió. Recarga la página, revísala y acéptala de nuevo.");
		}
	}
}
