package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A real physical damage. Groups one or more citizen Reports and is what the public map shows.
 *
 * <p>Central rule: only a new photo can close an Issue. An agency saying it fixed the damage moves the
 * Issue to {@link IssueStatus#REPAIR_CLAIMED} and no further; {@link IssueStatus#VERIFIED} and
 * {@link IssueStatus#REOPENED} are reachable only through {@link #applyVerification}.
 */
public final class Issue {

	private final UUID id;
	private final DamageType damageType;
	private final GeoPoint location;
	private final Instant openedAt;
	/** Responsible agency code; {@code null} when no rule covers the damage type (manual review). */
	private final String agencyCode;
	/** Public (blurred) photo shown on the map. */
	private final UUID coverMediaId;
	private IssueStatus status;
	private int reportCount;
	private final List<StatusTransition> transitions;

	private Issue(UUID id, DamageType damageType, GeoPoint location, Instant openedAt, String agencyCode,
			UUID coverMediaId, IssueStatus status, int reportCount, List<StatusTransition> transitions) {
		this.id = Objects.requireNonNull(id, "id");
		this.damageType = Objects.requireNonNull(damageType, "damageType");
		this.location = Objects.requireNonNull(location, "location");
		this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
		this.agencyCode = agencyCode;
		this.coverMediaId = coverMediaId;
		this.status = Objects.requireNonNull(status, "status");
		this.reportCount = reportCount;
		this.transitions = new ArrayList<>(transitions);
	}

	/** Opens an Issue from the first confirmed Report. */
	public static Issue open(UUID id, DamageType damageType, GeoPoint location, String agencyCode, UUID coverMediaId,
			String reportId, Instant at) {
		var issue = new Issue(id, damageType, location, at, agencyCode, coverMediaId, IssueStatus.REPORTED, 1, List.of());
		issue.transitions.add(new StatusTransition(null, IssueStatus.REPORTED, TransitionCause.CITIZEN_REPORT, reportId, at));
		return issue;
	}

	/** Rebuilds an Issue from storage. No rules are checked: the stored history is the source of truth. */
	public static Issue rehydrate(UUID id, DamageType damageType, GeoPoint location, Instant openedAt,
			String agencyCode, UUID coverMediaId, IssueStatus status, int reportCount,
			List<StatusTransition> transitions) {
		return new Issue(id, damageType, location, openedAt, agencyCode, coverMediaId, status, reportCount, transitions);
	}

	/** A duplicate Report of the same damage was confirmed. */
	public void addReport() {
		if (status.isClosed()) {
			throw new InvalidStatusTransitionException("Issue " + id + " is verified; a new report must open a new Issue");
		}
		reportCount++;
	}

	/** The petition (derecho de petición) was generated and sent to the agency. Also used after a reopening. */
	public void file(String petitionRef, Instant at) {
		moveTo(IssueStatus.FILED, TransitionCause.PETITION_FILED, petitionRef, at);
	}

	/**
	 * Records the agency's reply. A {@link AgencyResponseClassification#RESOLVES} reply also records the
	 * repair claim, but the Issue stays open until a photo verifies it.
	 */
	public void registerAgencyResponse(AgencyResponseClassification classification, String responseRef, Instant at) {
		Objects.requireNonNull(classification, "classification");
		moveTo(IssueStatus.AGENCY_RESPONDED, TransitionCause.AGENCY_RESPONSE, responseRef, at);
		if (classification == AgencyResponseClassification.RESOLVES) {
			moveTo(IssueStatus.REPAIR_CLAIMED, TransitionCause.AGENCY_RESPONSE, responseRef, at);
		}
	}

	/** The only way to reach VERIFIED, REOPENED or NEEDS_MORE_EVIDENCE. */
	public void applyVerification(VerificationResult result, Instant at) {
		Objects.requireNonNull(result, "result");
		moveTo(result.resultingStatus(), TransitionCause.PHOTO_VERIFICATION, result.evidenceMediaId(), at);
	}

	private void moveTo(IssueStatus target, TransitionCause cause, String evidenceRef, Instant at) {
		if (!status.canTransitionTo(target)) {
			throw new InvalidStatusTransitionException(status, target);
		}
		boolean needsPhoto = IssueStatus.EVIDENCE_ONLY.contains(target);
		if (needsPhoto != (cause == TransitionCause.PHOTO_VERIFICATION)) {
			throw new InvalidStatusTransitionException(status, target);
		}
		transitions.add(new StatusTransition(status, target, cause, evidenceRef, at));
		status = target;
	}

	public UUID id() {
		return id;
	}

	public DamageType damageType() {
		return damageType;
	}

	public GeoPoint location() {
		return location;
	}

	public Instant openedAt() {
		return openedAt;
	}

	public Optional<String> agencyCode() {
		return Optional.ofNullable(agencyCode);
	}

	public Optional<UUID> coverMediaId() {
		return Optional.ofNullable(coverMediaId);
	}

	public IssueStatus status() {
		return status;
	}

	public int reportCount() {
		return reportCount;
	}

	public List<StatusTransition> transitions() {
		return Collections.unmodifiableList(transitions);
	}
}
