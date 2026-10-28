package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.TransitionCause;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public timeline of an Issue. No reporter, petitioner or verifier data. */
public record IssueHistory(List<Step> steps, List<Response> agencyResponses, List<Check> verifications) {

	/** @param from null for the step that opened the Issue */
	public record Step(IssueStatus from, IssueStatus to, TransitionCause cause, Instant at) {
	}

	public record Response(Instant at, AgencyResponseClassification classification, String summary, String body) {
	}

	/** @param photoMediaId the new photo, blurred */
	public record Check(Instant at, Outcome outcome, String reason, UUID photoMediaId) {
	}
}
