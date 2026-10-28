package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.petition.Petitioner;
import com.civicrepairs.domain.shared.DamageType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Simulated filing of a derecho de petición with the citizen's own data. Moves the Issue to FILED. Nothing is sent
 * to the agency.
 */
public interface FilePetitionUseCase {

	/**
	 * @param policyVersion the data policy version the citizen accepted when authorizing the agency to receive their data
	 * @throws PetitionException PROFILE_REQUIRED, POLICY_OUTDATED, ISSUE_NOT_FOUND, ALREADY_FILED or NOT_FILEABLE
	 */
	PetitionReceipt file(UUID issueId, AuthenticatedUser user, String policyVersion);

	/** @throws PetitionException PETITION_NOT_FOUND unless {@code userId} filed it */
	PetitionDocument document(UUID petitionId, UUID userId);

	record PetitionReceipt(UUID petitionId, UUID issueId, String radicado, Instant filedAt, LocalDate dueDate,
			String agencyName) {
	}

	/** Everything the printable petition shows. Only its filer can see it. */
	record PetitionDocument(UUID petitionId, String radicado, Instant filedAt, LocalDate dueDate, String agencyName,
			Petitioner petitioner, String subject, UUID issueId, DamageType damageType, double latitude,
			double longitude, String localityName, int reportCount, Instant openedAt, UUID photoMediaId) {
	}
}
