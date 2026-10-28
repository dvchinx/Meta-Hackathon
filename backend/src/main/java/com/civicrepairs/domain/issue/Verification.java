package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.issue.VerificationResult.Outcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One before/after check of an Issue, with both signals kept for the audit trail.
 *
 * @param verifierId    salted hash of who sent the photo (phone number or account), never the person
 * @param publicMediaId the new photo, blurred: shown next to the original
 */
public record Verification(UUID id, UUID issueId, String verifierId, UUID photoMediaId, UUID publicMediaId,
		double areaBefore, double areaAfter, RepairVerdict verdict, Outcome outcome, Instant at) {

	public Verification {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(issueId, "issueId");
		Objects.requireNonNull(verifierId, "verifierId");
		Objects.requireNonNull(photoMediaId, "photoMediaId");
		Objects.requireNonNull(publicMediaId, "publicMediaId");
		Objects.requireNonNull(verdict, "verdict");
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(at, "at");
	}
}
