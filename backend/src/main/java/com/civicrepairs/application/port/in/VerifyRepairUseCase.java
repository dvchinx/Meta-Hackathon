package com.civicrepairs.application.port.in;

import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;

import java.util.UUID;

/**
 * Before/after check: the only way an Issue reaches VERIFIED, REOPENED or NEEDS_MORE_EVIDENCE. Accepted only while
 * the agency claims the repair (REPAIR_CLAIMED) or a previous photo was inconclusive.
 */
public interface VerifyRepairUseCase {

	/**
	 * @param verifierId salted hash of who sends the photo (same as a reporter id)
	 * @throws IssueActionException NOT_FOUND, WRONG_STATUS, RATE_LIMITED or MODELS_UNAVAILABLE
	 */
	VerificationReceipt verify(UUID issueId, String verifierId, Photo photo);

	/** @param photoMediaId the new photo, blurred (public) */
	record VerificationReceipt(UUID issueId, Outcome outcome, IssueStatus status, String reason, UUID photoMediaId) {
	}
}
