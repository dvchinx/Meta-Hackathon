package com.civicrepairs.domain.issue;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * REPORTED → FILED → AGENCY_RESPONDED → REPAIR_CLAIMED → VERIFIED
 *                                                      ↘ REOPENED → FILED
 * NEEDS_MORE_EVIDENCE: the before/after check was inconclusive; waits for another photo.
 * </pre>
 */
public enum IssueStatus {
	REPORTED,
	FILED,
	AGENCY_RESPONDED,
	REPAIR_CLAIMED,
	NEEDS_MORE_EVIDENCE,
	VERIFIED,
	REOPENED;

	/** Statuses that can only be reached with photographic evidence. */
	static final Set<IssueStatus> EVIDENCE_ONLY = EnumSet.of(VERIFIED, REOPENED, NEEDS_MORE_EVIDENCE);

	public boolean canTransitionTo(IssueStatus target) {
		return switch (this) {
			case REPORTED, REOPENED -> target == FILED;
			case FILED -> target == AGENCY_RESPONDED;
			case AGENCY_RESPONDED -> target == REPAIR_CLAIMED;
			case REPAIR_CLAIMED, NEEDS_MORE_EVIDENCE -> EVIDENCE_ONLY.contains(target);
			case VERIFIED -> false;
		};
	}

	public boolean isClosed() {
		return this == VERIFIED;
	}
}
