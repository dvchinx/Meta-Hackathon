package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.TestcontainersConfiguration;
import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.application.port.out.GeoQueryPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.VerificationResult;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "META_API_ENABLED=false")
class IssuePersistenceTest {

	private static final Instant T0 = Instant.parse("2026-10-26T15:00:00Z");
	// Parque de la 93: ~0.00027° of latitude ≈ 30 m.
	private static final GeoPoint PARQUE_93 = new GeoPoint(4.676389, -74.048611);

	@Autowired
	IssueRepositoryPort issues;
	@Autowired
	GeoQueryPort geo;
	@Autowired
	AgencyRepositoryPort agencies;
	@Autowired
	PublicIssueReadPort publicIssues;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	TransactionTemplate tx;

	@BeforeEach
	void clean() {
		jdbc.sql("DELETE FROM petition").update();
		jdbc.sql("DELETE FROM status_transition").update();
		jdbc.sql("DELETE FROM issue").update();
	}

	private static Issue pothole(GeoPoint where) {
		return Issue.open(UUID.randomUUID(), DamageType.POTHOLE, where, "IDU", UUID.randomUUID(), "report-1", T0);
	}

	private static GeoPoint northOf(GeoPoint p, double meters) {
		return new GeoPoint(p.latitude() + meters / 111_320.0, p.longitude());
	}

	@Test
	void savesAndReloadsAnIssueWithItsHistory() {
		var issue = pothole(PARQUE_93);
		issues.save(issue);

		var reloaded = issues.findById(issue.id()).orElseThrow();

		assertThat(reloaded.status()).isEqualTo(IssueStatus.REPORTED);
		assertThat(reloaded.location()).isEqualTo(PARQUE_93);
		assertThat(reloaded.agencyCode()).contains("IDU");
		assertThat(reloaded.coverMediaId()).isEqualTo(issue.coverMediaId());
		assertThat(reloaded.transitions()).isEqualTo(issue.transitions());
	}

	@Test
	void appendsNewTransitionsWithoutRewritingOldOnes() {
		var issue = pothole(PARQUE_93);
		issues.save(issue);

		var loaded = issues.findById(issue.id()).orElseThrow();
		loaded.addReport();
		loaded.file("petition-1", T0.plus(1, ChronoUnit.DAYS));
		loaded.registerAgencyResponse(AgencyResponseClassification.RESOLVES, "response-1", T0.plus(5, ChronoUnit.DAYS));
		loaded.applyVerification(new VerificationResult(VerificationResult.Outcome.REPAIRED, "media-after"),
				T0.plus(9, ChronoUnit.DAYS));
		issues.save(loaded);

		var reloaded = issues.findById(issue.id()).orElseThrow();
		assertThat(reloaded.status()).isEqualTo(IssueStatus.VERIFIED);
		assertThat(reloaded.reportCount()).isEqualTo(2);
		assertThat(reloaded.transitions()).isEqualTo(loaded.transitions()).hasSize(5);
		assertThat(jdbc.sql("SELECT count(*) FROM status_transition WHERE issue_id = ?").param(issue.id())
				.query(Integer.class).single()).isEqualTo(5);
	}

	@Test
	void findsOpenIssuesOfTheSameTypeWithin30Meters() {
		var near = pothole(northOf(PARQUE_93, 20));
		var far = pothole(northOf(PARQUE_93, 45));
		var otherType = Issue.open(UUID.randomUUID(), DamageType.OPEN_MANHOLE, PARQUE_93, "EAAB", null, "r", T0);
		issues.save(near);
		issues.save(far);
		issues.save(otherType);

		assertThat(geo.findOpenIssuesNear(PARQUE_93, 30, DamageType.POTHOLE, 5)).containsExactly(near.id());
	}

	@Test
	void nearestCandidateComesFirst() {
		var at25 = pothole(northOf(PARQUE_93, 25));
		var at5 = pothole(northOf(PARQUE_93, 5));
		issues.save(at25);
		issues.save(at5);

		assertThat(geo.findOpenIssuesNear(PARQUE_93, 30, DamageType.POTHOLE, 5)).containsExactly(at5.id(), at25.id());
	}

	@Test
	void verifiedIssuesAreNeverDedupCandidates() {
		var issue = pothole(PARQUE_93);
		issue.file("p", T0);
		issue.registerAgencyResponse(AgencyResponseClassification.RESOLVES, "r", T0);
		issue.applyVerification(new VerificationResult(VerificationResult.Outcome.REPAIRED, "m"), T0);
		issues.save(issue);

		assertThat(geo.findOpenIssuesNear(PARQUE_93, 30, DamageType.POTHOLE, 5)).isEmpty();
	}

	@Test
	void locksOnlyInsideATransaction() {
		var issue = pothole(PARQUE_93);
		issues.save(issue);

		assertThatThrownBy(() -> issues.findByIdForUpdate(issue.id()))
				.isInstanceOf(IllegalTransactionStateException.class);
		Optional<Issue> locked = tx.execute(status -> issues.findByIdForUpdate(issue.id()));
		assertThat(locked).isPresent();
	}

	@Test
	void resolvesTheResponsibleAgencyByDamageTypeAndAssetOwner() {
		assertThat(agency(DamageType.POTHOLE, AssetOwner.UNKNOWN, "02")).isEqualTo("UMV");
		// Lids: the Acueducto by default, unless the markings say otherwise.
		assertThat(agency(DamageType.OPEN_MANHOLE, AssetOwner.UNKNOWN, "02")).isEqualTo("EAAB");
		assertThat(agency(DamageType.OPEN_MANHOLE, AssetOwner.WATER, "02")).isEqualTo("EAAB");
		assertThat(agency(DamageType.OPEN_MANHOLE, AssetOwner.POWER, "02")).isEqualTo("ENEL");
		assertThat(agency(DamageType.OPEN_MANHOLE, AssetOwner.GAS, "02")).isEqualTo("VANTI");
		assertThat(agency(DamageType.DAMAGED_POLE, AssetOwner.STREET_LIGHTING, "02")).isEqualTo("UAESP");
		assertThat(agency(DamageType.DAMAGED_POLE, AssetOwner.UNKNOWN, "02")).isEqualTo("ENEL");
		assertThat(agency(DamageType.GARBAGE_DUMP, AssetOwner.UNKNOWN, "02")).isEqualTo("UAESP");
		// Sidewalks go to the Alcaldía Local of the damage's locality.
		assertThat(agency(DamageType.DAMAGED_SIDEWALK, AssetOwner.UNKNOWN, "08")).isEqualTo("AL08");
		assertThat(agencies.findResponsibleFor(DamageType.DAMAGED_SIDEWALK, AssetOwner.UNKNOWN, "08")).get()
				.extracting("name").isEqualTo("Alcaldía Local de Kennedy");
		assertThat(agencies.findResponsibleFor(DamageType.DAMAGED_SIDEWALK, AssetOwner.UNKNOWN, null)).isEmpty();
		assertThat(agencies.findResponsibleFor(DamageType.OTHER, AssetOwner.UNKNOWN, "02")).isEmpty();
	}

	@Test
	void everySupportedDamageTypeHasARuleAndAnOfficialChannel() {
		for (DamageType type : DamageType.values()) {
			if (type.requiresManualReview()) {
				continue;
			}
			assertThat(agencies.findResponsibleFor(type, AssetOwner.UNKNOWN, "11")).as(type.name()).isPresent();
			assertThat(jdbc.sql("SELECT count(*) FROM official_channel WHERE damage_type = :t AND agency_code IS NULL")
					.param("t", type.name()).query(Integer.class).single()).as(type.name()).isEqualTo(1);
		}
	}

	@Test
	void findsTheLocalityOfAPoint() {
		assertThat(geo.findLocality(PARQUE_93)).get().extracting("name").isEqualTo("Chapinero");
		assertThat(geo.findLocality(new GeoPoint(4.6280, -74.1580))).get().extracting("name").isEqualTo("Kennedy");
		// Neighbouring municipalities: Soacha (inside the rough box around Bogotá) and Chía.
		assertThat(geo.findLocality(new GeoPoint(4.5794, -74.2168))).isEmpty();
		assertThat(geo.findLocality(new GeoPoint(4.8615, -74.0582))).isEmpty();
	}

	@Test
	void publicProjectionIncludesAgencyLocalityAndOfficialChannel() {
		var issue = pothole(PARQUE_93);
		issues.save(issue);

		assertThat(publicIssues.findAllPublic()).singleElement().satisfies(p -> {
			assertThat(p.id()).isEqualTo(issue.id());
			assertThat(p.agencyName()).isEqualTo("Instituto de Desarrollo Urbano");
			assertThat(p.localityName()).isEqualTo("Chapinero");
			assertThat(p.channel().url()).isEqualTo("https://bogota.gov.co/yo-participo/reporta-sdqs/huecos");
			assertThat(p.petition()).isNull();
			assertThat(p.latitude()).isEqualTo(PARQUE_93.latitude());
		});
		assertThat(publicIssues.findPublic(issue.id())).isPresent();
		assertThat(publicIssues.findPublic(UUID.randomUUID())).isEmpty();
	}

	@Test
	void theAgencysOwnChannelWinsOverTheDefault() {
		var lid = Issue.open(UUID.randomUUID(), DamageType.OPEN_MANHOLE, PARQUE_93, "ENEL", null, "r", T0);
		issues.save(lid);

		assertThat(publicIssues.findPublic(lid.id())).get()
				.satisfies(p -> assertThat(p.channel().phone()).isEqualTo("Línea 115"));
	}

	private String agency(DamageType type, AssetOwner owner, String locality) {
		return agencies.findResponsibleFor(type, owner, locality).orElseThrow().code();
	}
}
