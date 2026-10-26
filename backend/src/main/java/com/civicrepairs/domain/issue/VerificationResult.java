package com.civicrepairs.domain.issue;

import java.util.Objects;

/**
 * Outcome of comparing a new photo with the original damage (SAM mask areas + Muse Spark judgement).
 * Only {@code VerifyRepairUseCase} builds one, and it always carries the photo that backs it.
 */
public record VerificationResult(Outcome outcome, String evidenceMediaId) {

	public enum Outcome {
		/** Both signals agree the damage is gone. */
		REPAIRED,
		/** Both signals agree the damage is still there. */
		NOT_REPAIRED,
		/** The signals disagree or the photo is unusable. */
		INCONCLUSIVE
	}

	public VerificationResult {
		Objects.requireNonNull(outcome, "outcome");
		if (evidenceMediaId == null || evidenceMediaId.isBlank()) {
			throw new IllegalArgumentException("a verification needs a photo as evidence");
		}
	}

	IssueStatus resultingStatus() {
		return switch (outcome) {
			case REPAIRED -> IssueStatus.VERIFIED;
			case NOT_REPAIRED -> IssueStatus.REOPENED;
			case INCONCLUSIVE -> IssueStatus.NEEDS_MORE_EVIDENCE;
		};
	}
}
