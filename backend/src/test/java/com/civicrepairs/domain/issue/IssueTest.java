package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static com.civicrepairs.domain.issue.IssueStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueTest {

	private static final Instant T0 = Instant.parse("2026-10-26T15:00:00Z");
	private static final GeoPoint CHAPINERO = new GeoPoint(4.6486, -74.0628);

	private static Issue newIssue() {
		return Issue.open(UUID.randomUUID(), DamageType.POTHOLE, CHAPINERO, "IDU", UUID.randomUUID(), "report-1", T0);
	}

	private static Issue repairClaimedIssue() {
		var issue = newIssue();
		issue.file("petition-1", T0);
		issue.registerAgencyResponse(AgencyResponseClassification.RESOLVES, "response-1", T0);
		return issue;
	}

	@Test
	void opensAsReportedWithOneReportAndAnOpeningTransition() {
		var issue = newIssue();

		assertThat(issue.status()).isEqualTo(REPORTED);
		assertThat(issue.reportCount()).isEqualTo(1);
		assertThat(issue.transitions()).singleElement().satisfies(t -> {
			assertThat(t.from()).isNull();
			assertThat(t.to()).isEqualTo(REPORTED);
			assertThat(t.cause()).isEqualTo(TransitionCause.CITIZEN_REPORT);
			assertThat(t.evidenceRef()).isEqualTo("report-1");
		});
	}

	@Test
	void followsTheHappyPathToVerifiedAndRecordsEveryTransition() {
		var issue = repairClaimedIssue();

		issue.applyVerification(new VerificationResult(Outcome.REPAIRED, "media-after-1"), T0);

		assertThat(issue.status()).isEqualTo(VERIFIED);
		assertThat(issue.transitions()).extracting(StatusTransition::to)
				.containsExactly(REPORTED, FILED, AGENCY_RESPONDED, REPAIR_CLAIMED, VERIFIED);
		assertThat(issue.transitions().getLast().evidenceRef()).isEqualTo("media-after-1");
	}

	@Test
	void agencySayingItIsFixedOnlyClaimsTheRepair() {
		var issue = repairClaimedIssue();

		assertThat(issue.status()).isEqualTo(REPAIR_CLAIMED);
		assertThat(issue.status().isClosed()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = AgencyResponseClassification.class, names = {"EVASIVE", "NO_RESPONSE"})
	void evasiveOrEmptyResponsesStayAtAgencyResponded(AgencyResponseClassification classification) {
		var issue = newIssue();
		issue.file("petition-1", T0);

		issue.registerAgencyResponse(classification, "response-1", T0);

		assertThat(issue.status()).isEqualTo(AGENCY_RESPONDED);
	}

	@Test
	void failedVerificationReopensAndCanBeFiledAgain() {
		var issue = repairClaimedIssue();

		issue.applyVerification(new VerificationResult(Outcome.NOT_REPAIRED, "media-after-1"), T0);
		assertThat(issue.status()).isEqualTo(REOPENED);

		issue.file("petition-2", T0);
		assertThat(issue.status()).isEqualTo(FILED);
	}

	@Test
	void inconclusiveVerificationAsksForMoreEvidenceAndANewPhotoCanResolveIt() {
		var issue = repairClaimedIssue();

		issue.applyVerification(new VerificationResult(Outcome.INCONCLUSIVE, "media-after-1"), T0);
		assertThat(issue.status()).isEqualTo(NEEDS_MORE_EVIDENCE);

		issue.applyVerification(new VerificationResult(Outcome.REPAIRED, "media-after-2"), T0);
		assertThat(issue.status()).isEqualTo(VERIFIED);
	}

	@Test
	void cannotBeVerifiedBeforeTheAgencyClaimsTheRepair() {
		var issue = newIssue();
		issue.file("petition-1", T0);

		assertThatThrownBy(() -> issue.applyVerification(new VerificationResult(Outcome.REPAIRED, "media-1"), T0))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThat(issue.status()).isEqualTo(FILED);
	}

	@Test
	void verificationWithoutAPhotoIsImpossible() {
		assertThatThrownBy(() -> new VerificationResult(Outcome.REPAIRED, " "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsSkippingSteps() {
		var issue = newIssue();

		assertThatThrownBy(() -> issue.registerAgencyResponse(AgencyResponseClassification.RESOLVES, "r", T0))
				.isInstanceOf(InvalidStatusTransitionException.class);
		assertThat(issue.transitions()).hasSize(1);
	}

	@Test
	void verifiedIssueIsFinal() {
		var issue = repairClaimedIssue();
		issue.applyVerification(new VerificationResult(Outcome.REPAIRED, "media-after-1"), T0);

		assertThatThrownBy(() -> issue.file("petition-2", T0)).isInstanceOf(InvalidStatusTransitionException.class);
		assertThatThrownBy(issue::addReport).isInstanceOf(InvalidStatusTransitionException.class);
	}

	@Test
	void duplicateReportsIncreaseTheCounter() {
		var issue = newIssue();

		issue.addReport();
		issue.addReport();

		assertThat(issue.reportCount()).isEqualTo(3);
	}

	@Test
	void transitionHistoryIsReadOnly() {
		var issue = newIssue();

		assertThatThrownBy(() -> issue.transitions().clear()).isInstanceOf(UnsupportedOperationException.class);
	}
}
