package com.civicrepairs.domain.report;

import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import com.civicrepairs.domain.shared.ServiceArea;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One citizen submission. Data arrives in any order (photo, text, location, possibly in one message);
 * the bot only asks for what is missing. An Issue is created or updated only after the citizen confirms.
 */
public final class Report {

	private final UUID id;
	private final ReportChannel channel;
	/** Salted hash of the phone number (or a web session token). Never the number itself. */
	private final String reporterId;
	private final Instant createdAt;
	private ReportStatus status;
	private UUID photoMediaId;
	private String context;
	private GeoPoint location;
	private Interpretation interpretation;
	private DamageAnalysis analysis;
	private UUID issueId;
	private String reviewReason;

	private Report(UUID id, ReportChannel channel, String reporterId, Instant createdAt, ReportStatus status) {
		this.id = Objects.requireNonNull(id, "id");
		this.channel = Objects.requireNonNull(channel, "channel");
		this.reporterId = Objects.requireNonNull(reporterId, "reporterId");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.status = Objects.requireNonNull(status, "status");
	}

	public static Report start(UUID id, ReportChannel channel, String reporterId, Instant at) {
		return new Report(id, channel, reporterId, at, ReportStatus.COLLECTING);
	}

	public static Report rehydrate(UUID id, ReportChannel channel, String reporterId, Instant createdAt,
			ReportStatus status, UUID photoMediaId, String context, GeoPoint location, Interpretation interpretation,
			DamageAnalysis analysis, UUID issueId, String reviewReason) {
		var report = new Report(id, channel, reporterId, createdAt, status);
		report.photoMediaId = photoMediaId;
		report.context = context;
		report.location = location;
		report.interpretation = interpretation;
		report.analysis = analysis;
		report.issueId = issueId;
		report.reviewReason = reviewReason;
		return report;
	}

	/** A new photo replaces the previous one (e.g. after a rejection). */
	public void attachPhoto(UUID mediaId) {
		requireStatus(ReportStatus.COLLECTING);
		this.photoMediaId = Objects.requireNonNull(mediaId, "mediaId");
	}

	public void addContext(String text) {
		requireStatus(ReportStatus.COLLECTING);
		if (text == null || text.isBlank()) {
			return;
		}
		this.context = context == null ? text.strip() : context + "\n" + text.strip();
	}

	/** @return false when the point is outside Bogotá; the location is then not stored. */
	public boolean setLocation(GeoPoint point) {
		requireStatus(ReportStatus.COLLECTING);
		if (!ServiceArea.BOGOTA.contains(point)) {
			return false;
		}
		this.location = point;
		return true;
	}

	public Set<MissingItem> missing() {
		Set<MissingItem> missing = EnumSet.noneOf(MissingItem.class);
		if (photoMediaId == null) {
			missing.add(MissingItem.PHOTO);
		}
		if (location == null) {
			missing.add(MissingItem.LOCATION);
		}
		return missing;
	}

	public boolean isReadyForAnalysis() {
		return status == ReportStatus.COLLECTING && missing().isEmpty();
	}

	public void startAnalysis() {
		if (!isReadyForAnalysis()) {
			throw new InvalidReportStateException("Report " + id + " is not ready for analysis: missing " + missing());
		}
		status = ReportStatus.ANALYZING;
	}

	/**
	 * Applies the model outputs. {@code analysis} may be null when the photo was rejected before segmentation.
	 */
	public AnalysisOutcome completeAnalysis(Interpretation interpretation, DamageAnalysis analysis) {
		requireStatus(ReportStatus.ANALYZING);
		this.interpretation = Objects.requireNonNull(interpretation, "interpretation");
		if (!interpretation.isAcceptable()) {
			this.photoMediaId = null;
			this.analysis = null;
			status = ReportStatus.COLLECTING;
			return AnalysisOutcome.PHOTO_REJECTED;
		}
		this.analysis = Objects.requireNonNull(analysis, "analysis is required for an acceptable interpretation");
		if (interpretation.damageType() == DamageType.OTHER) {
			sendToManualReview("damage type outside the MVP scope");
			return AnalysisOutcome.MANUAL_REVIEW;
		}
		status = ReportStatus.AWAITING_CONFIRMATION;
		return AnalysisOutcome.AWAITING_CONFIRMATION;
	}

	/** Model output invalid twice, model unavailable or budget exhausted. */
	public void sendToManualReview(String reason) {
		if (!status.isOpen()) {
			throw new InvalidReportStateException("Report " + id + " is already " + status);
		}
		this.reviewReason = reason;
		status = ReportStatus.MANUAL_REVIEW;
	}

	public void confirm(UUID issueId) {
		requireStatus(ReportStatus.AWAITING_CONFIRMATION);
		this.issueId = Objects.requireNonNull(issueId, "issueId");
		status = ReportStatus.CONFIRMED;
	}

	public void cancel() {
		if (!status.isOpen()) {
			throw new InvalidReportStateException("Report " + id + " is already " + status);
		}
		status = ReportStatus.CANCELLED;
	}

	private void requireStatus(ReportStatus expected) {
		if (status != expected) {
			throw new InvalidReportStateException("Report " + id + " is " + status + ", expected " + expected);
		}
	}

	public UUID id() {
		return id;
	}

	public ReportChannel channel() {
		return channel;
	}

	public String reporterId() {
		return reporterId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public ReportStatus status() {
		return status;
	}

	public Optional<UUID> photoMediaId() {
		return Optional.ofNullable(photoMediaId);
	}

	public Optional<String> context() {
		return Optional.ofNullable(context);
	}

	public Optional<GeoPoint> location() {
		return Optional.ofNullable(location);
	}

	public Optional<Interpretation> interpretation() {
		return Optional.ofNullable(interpretation);
	}

	public Optional<DamageAnalysis> analysis() {
		return Optional.ofNullable(analysis);
	}

	public Optional<UUID> issueId() {
		return Optional.ofNullable(issueId);
	}

	public Optional<String> reviewReason() {
		return Optional.ofNullable(reviewReason);
	}
}
