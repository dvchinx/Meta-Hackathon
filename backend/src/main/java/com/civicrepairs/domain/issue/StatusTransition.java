package com.civicrepairs.domain.issue;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of a status change. Never deleted.
 *
 * @param from        previous status, {@code null} for the transition that opens the Issue
 * @param evidenceRef what backs the change: report id, petition id, agency response id or verification media id
 */
public record StatusTransition(IssueStatus from, IssueStatus to, TransitionCause cause, String evidenceRef, Instant at) {

	public StatusTransition {
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(evidenceRef, "evidenceRef");
		Objects.requireNonNull(at, "at");
	}
}
