package com.civicrepairs.domain.report;

import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import com.civicrepairs.domain.shared.Urgency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

	private static final Instant T0 = Instant.parse("2026-10-26T15:00:00Z");
	private static final GeoPoint CHAPINERO = new GeoPoint(4.6486, -74.0628);
	private static final GeoPoint MEDELLIN = new GeoPoint(6.2442, -75.5812);

	private static Report newReport() {
		return Report.start(UUID.randomUUID(), ReportChannel.WHATSAPP, "hash-123", T0);
	}

	private static Interpretation interpretation(DamageType type, boolean valid, double confidence) {
		return new Interpretation(type, Urgency.HIGH, valid, List.of("frente al parque"), "Hueco profundo", confidence);
	}

	private static DamageAnalysis analysis() {
		return new DamageAnalysis(0.12, 1, UUID.randomUUID(), UUID.randomUUID());
	}

	private static Report analyzing() {
		var report = newReport();
		report.attachPhoto(UUID.randomUUID());
		report.setLocation(CHAPINERO);
		report.startAnalysis();
		return report;
	}

	@Test
	void asksOnlyForWhatIsMissingInAnyOrder() {
		var report = newReport();
		assertThat(report.missing()).containsExactlyInAnyOrder(MissingItem.PHOTO, MissingItem.LOCATION);

		report.setLocation(CHAPINERO);
		assertThat(report.missing()).containsExactly(MissingItem.PHOTO);

		report.addContext("Está muy profundo");
		report.attachPhoto(UUID.randomUUID());
		assertThat(report.missing()).isEmpty();
		assertThat(report.isReadyForAnalysis()).isTrue();
	}

	@Test
	void rejectsLocationsOutsideBogota() {
		var report = newReport();

		assertThat(report.setLocation(MEDELLIN)).isFalse();
		assertThat(report.location()).isEmpty();
		assertThat(report.missing()).contains(MissingItem.LOCATION);
	}

	@Test
	void concatenatesContextFromSeveralMessages() {
		var report = newReport();
		report.addContext("Hay un hueco");
		report.addContext("  ");
		report.addContext("lleva dos semanas");

		assertThat(report.context()).contains("Hay un hueco\nlleva dos semanas");
	}

	@Test
	void cannotAnalyzeWithoutPhotoAndLocation() {
		var report = newReport();
		report.attachPhoto(UUID.randomUUID());

		assertThatThrownBy(report::startAnalysis).isInstanceOf(InvalidReportStateException.class);
	}

	@Test
	void acceptableInterpretationWaitsForConfirmation() {
		var report = analyzing();

		var outcome = report.completeAnalysis(interpretation(DamageType.POTHOLE, true, 0.9), analysis());

		assertThat(outcome).isEqualTo(AnalysisOutcome.AWAITING_CONFIRMATION);
		assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_CONFIRMATION);
	}

	@Test
	void lowConfidenceAsksForAnotherPhotoAndKeepsTheRest() {
		var report = analyzing();
		report.completeAnalysis(interpretation(DamageType.POTHOLE, true, 0.59), null);

		assertThat(report.status()).isEqualTo(ReportStatus.COLLECTING);
		assertThat(report.missing()).containsExactly(MissingItem.PHOTO);
		assertThat(report.location()).contains(CHAPINERO);
	}

	@Test
	void notUrbanDamageAsksForAnotherPhoto() {
		var report = analyzing();

		var outcome = report.completeAnalysis(interpretation(DamageType.OTHER, false, 0.95), null);

		assertThat(outcome).isEqualTo(AnalysisOutcome.PHOTO_REJECTED);
	}

	@Test
	void otherDamageGoesToManualReview() {
		var report = analyzing();

		var outcome = report.completeAnalysis(interpretation(DamageType.OTHER, true, 0.9), analysis());

		assertThat(outcome).isEqualTo(AnalysisOutcome.MANUAL_REVIEW);
		assertThat(report.status()).isEqualTo(ReportStatus.MANUAL_REVIEW);
		assertThat(report.reviewReason()).isPresent();
	}

	@Test
	void acceptableInterpretationNeedsTheSegmentation() {
		var report = analyzing();

		assertThatThrownBy(() -> report.completeAnalysis(interpretation(DamageType.POTHOLE, true, 0.9), null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void confirmationLinksTheIssue() {
		var report = analyzing();
		report.completeAnalysis(interpretation(DamageType.POTHOLE, true, 0.9), analysis());
		var issueId = UUID.randomUUID();

		report.confirm(issueId);

		assertThat(report.status()).isEqualTo(ReportStatus.CONFIRMED);
		assertThat(report.issueId()).contains(issueId);
	}

	@Test
	void cannotConfirmBeforeTheAnalysis() {
		var report = newReport();

		assertThatThrownBy(() -> report.confirm(UUID.randomUUID())).isInstanceOf(InvalidReportStateException.class);
	}

	@Test
	void closedReportsCannotBeCancelledOrEdited() {
		var report = analyzing();
		report.completeAnalysis(interpretation(DamageType.POTHOLE, true, 0.9), analysis());
		report.confirm(UUID.randomUUID());

		assertThatThrownBy(report::cancel).isInstanceOf(InvalidReportStateException.class);
		assertThatThrownBy(() -> report.attachPhoto(UUID.randomUUID())).isInstanceOf(InvalidReportStateException.class);
	}

	@Test
	void interpretationConfidenceMustBeAProbability() {
		assertThatThrownBy(() -> interpretation(DamageType.POTHOLE, true, 1.2))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
