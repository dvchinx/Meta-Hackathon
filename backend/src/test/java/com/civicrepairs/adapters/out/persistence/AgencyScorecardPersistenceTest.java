package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.application.port.out.AgencyScorecardReadPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.VerificationResult;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "META_API_ENABLED=false")
class AgencyScorecardPersistenceTest {

	private static final GeoPoint PARQUE_93 = new GeoPoint(4.676389, -74.048611);
	private static final Instant OPENED = Instant.parse("2026-10-01T15:00:00Z");
	private static final Instant FILED = Instant.parse("2026-10-02T15:00:00Z");
	private static final Instant REPLIED = Instant.parse("2026-10-10T15:00:00Z");
	// Monday 2026-11-09 in Bogotá: a petition filed on 2026-10-02 was due weeks ago.
	private static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
	private static final LocalDate DUE = LocalDate.of(2026, 10, 26);

	@Autowired
	IssueRepositoryPort issues;
	@Autowired
	AgencyScorecardReadPort scorecards;
	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		for (String table : new String[] {"notification_outbox", "pending_verification", "issue_follower",
				"verification", "agency_response", "petition", "status_transition", "issue"}) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}

	@Test
	void countsHowEachAgencyAnswersAndWhetherItsRepairsHoldUp() {
		// UMV: one petition ignored past its deadline, one evasive reply, one repair refuted by a photo and one
		// repair confirmed by a photo (a demo example).
		var ignored = filed(pothole());
		var evaded = filed(pothole());
		reply(evaded, AgencyResponseClassification.EVASIVE);
		var refuted = filed(pothole());
		reply(refuted, AgencyResponseClassification.RESOLVES);
		refuted.applyVerification(new VerificationResult(Outcome.NOT_REPAIRED, "after"), REPLIED.plusSeconds(60));
		var confirmed = filed(pothole());
		reply(confirmed, AgencyResponseClassification.RESOLVES);
		confirmed.applyVerification(new VerificationResult(Outcome.REPAIRED, "after"), REPLIED.plusSeconds(60));
		for (Issue issue : new Issue[] {ignored, evaded, refuted, confirmed}) {
			issues.save(issue);
			petition(issue);
		}
		reply(evaded.id(), "EVASIVE");
		reply(refuted.id(), "RESOLVES");
		reply(confirmed.id(), "RESOLVES");
		jdbc.sql("UPDATE issue SET seed_batch = 'demo' WHERE id = :id").param("id", confirmed.id()).update();
		// EAAB: a lid nobody has filed a petition for yet.
		issues.save(Issue.open(UUID.randomUUID(), DamageType.OPEN_MANHOLE, PARQUE_93, "EAAB", null, "r", OPENED));

		var cards = scorecards.scorecards(NOW, TODAY);

		assertThat(cards).extracting("agencyCode").containsExactly("UMV", "EAAB");
		var umv = cards.getFirst();
		assertThat(umv.issues()).isEqualTo(4);
		assertThat(umv.open()).isEqualTo(3);
		assertThat(umv.verified()).isEqualTo(1);
		assertThat(umv.averageDaysOpen()).isCloseTo(39.0, within(0.01));
		assertThat(umv.filed()).isEqualTo(4);
		assertThat(umv.overduePetitions()).isEqualTo(1);
		assertThat(umv.resolvingResponses()).isEqualTo(2);
		assertThat(umv.evasiveResponses()).isEqualTo(1);
		assertThat(umv.repairClaims()).isEqualTo(2);
		assertThat(umv.claimsConfirmed()).isEqualTo(1);
		assertThat(umv.claimsRefuted()).isEqualTo(1);
		assertThat(umv.examples()).isEqualTo(1);
		var eaab = cards.get(1);
		assertThat(eaab.open()).isEqualTo(1);
		assertThat(eaab.filed()).isZero();
		assertThat(eaab.overduePetitions()).isZero();
		assertThat(eaab.examples()).isZero();
	}

	@Test
	void anAgencyWithNothingOpenHasNoAverageAge() {
		var issue = filed(pothole());
		reply(issue, AgencyResponseClassification.RESOLVES);
		issue.applyVerification(new VerificationResult(Outcome.REPAIRED, "after"), REPLIED);
		issues.save(issue);

		assertThat(scorecards.scorecards(NOW, TODAY)).singleElement()
				.satisfies(card -> assertThat(card.averageDaysOpen()).isNull());
	}

	private static Issue pothole() {
		return Issue.open(UUID.randomUUID(), DamageType.POTHOLE, PARQUE_93, "UMV", null, "report", OPENED);
	}

	private static Issue filed(Issue issue) {
		issue.file("petition", FILED);
		return issue;
	}

	private static void reply(Issue issue, AgencyResponseClassification classification) {
		issue.registerAgencyResponse(classification, "response", REPLIED);
	}

	private void petition(Issue issue) {
		jdbc.sql("""
				INSERT INTO petition (id, issue_id, agency_code, radicado, filed_at, due_date, subject, filer_data,
				                      consent_policy_version)
				VALUES (:id, :issue, 'UMV', :radicado, :filed, :due, 'Hueco', :data, '2026-10-27')
				""")
				.param("id", UUID.randomUUID())
				.param("issue", issue.id())
				.param("radicado", "SIM-TEST-" + issue.id())
				.param("filed", Timestamp.from(FILED))
				.param("due", Date.valueOf(DUE))
				.param("data", new byte[] {1})
				.update();
	}

	private void reply(UUID issueId, String classification) {
		jdbc.sql("""
				INSERT INTO agency_response (id, issue_id, body, classification, summary, received_at)
				VALUES (:id, :issue, 'Respuesta', :classification, 'Resumen', :at)
				""")
				.param("id", UUID.randomUUID())
				.param("issue", issueId)
				.param("classification", classification)
				.param("at", Timestamp.from(REPLIED))
				.update();
	}
}
