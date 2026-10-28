package com.civicrepairs.domain.issue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An agency's reply to a petition and Muse Spark's reading of it. Public: it is the agency's word, which never
 * closes an Issue by itself.
 *
 * @param summary one or two sentences in Spanish for the public timeline
 */
public record AgencyResponse(UUID id, UUID issueId, String body, AgencyResponseClassification classification,
		String summary, Instant receivedAt) {

	public static final int MAX_BODY = 4000;

	public AgencyResponse {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(issueId, "issueId");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(classification, "classification");
		Objects.requireNonNull(summary, "summary");
		Objects.requireNonNull(receivedAt, "receivedAt");
		if (body.isBlank() || body.length() > MAX_BODY) {
			throw new IllegalArgumentException("body must have 1 to " + MAX_BODY + " characters");
		}
	}

	/** Muse Spark's reading of an agency reply, before it is attached to an Issue. */
	public record Reading(AgencyResponseClassification classification, String summary, double confidence) {

		public Reading {
			Objects.requireNonNull(classification, "classification");
			Objects.requireNonNull(summary, "summary");
		}
	}
}
