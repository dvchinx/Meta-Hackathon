package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.issue.RepairVerdict.Judgement;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.shared.DamageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCheckTest {

	private static RepairVerdict muse(Judgement judgement, double confidence) {
		return new RepairVerdict(judgement, confidence, "motivo");
	}

	@Test
	void bothSignalsMustAgreeForDamageThatDisappears() {
		// Pothole: 12% of the photo before, 1% after, and Muse sees it filled.
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.12, 0.01, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.REPAIRED);
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.12, 0.10, muse(Judgement.NOT_REPAIRED, 0.9)))
				.isEqualTo(Outcome.NOT_REPAIRED);
		// They disagree: Muse says repaired, but SAM still finds most of the hole.
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.12, 0.10, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.INCONCLUSIVE);
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.12, 0.0, muse(Judgement.NOT_REPAIRED, 0.9)))
				.isEqualTo(Outcome.INCONCLUSIVE);
	}

	@Test
	void forObjectsThatStayOnlyMuseCounts() {
		// A straightened pole is still a pole: its mask does not shrink.
		assertThat(RepairCheck.decide(DamageType.DAMAGED_POLE, 0.3, 0.3, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.REPAIRED);
		assertThat(RepairCheck.decide(DamageType.RISKY_TREE, 0.4, 0.4, muse(Judgement.NOT_REPAIRED, 0.9)))
				.isEqualTo(Outcome.NOT_REPAIRED);
	}

	@Test
	void doubtIsNeverAVerdict() {
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.12, 0.0, muse(Judgement.REPAIRED, 0.5)))
				.isEqualTo(Outcome.INCONCLUSIVE);
		assertThat(RepairCheck.decide(DamageType.DAMAGED_POLE, 0.3, 0.3, muse(Judgement.INCONCLUSIVE, 0.99)))
				.isEqualTo(Outcome.INCONCLUSIVE);
	}

	@Test
	void closingAnIssueNeedsMoreCertaintyThanReopeningIt() {
		// The evaluation's unsafe case: the same avenue from another angle, judged repaired with 0.82.
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.02, 0.0, muse(Judgement.REPAIRED, 0.82)))
				.isEqualTo(Outcome.INCONCLUSIVE);
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.02, 0.02, muse(Judgement.NOT_REPAIRED, 0.82)))
				.isEqualTo(Outcome.NOT_REPAIRED);
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.02, 0.0, muse(Judgement.REPAIRED, 0.85)))
				.isEqualTo(Outcome.REPAIRED);
	}

	@Test
	void withNoDamageMeasuredBeforeMuseDecidesAloneInBothDirections() {
		// SAM found no leak in the original photo: 0 % then and 0 % now must not approve or block anything by itself.
		assertThat(RepairCheck.decide(DamageType.WATER_LEAK, 0.0, 0.0, muse(Judgement.NOT_REPAIRED, 0.9)))
				.isEqualTo(Outcome.NOT_REPAIRED);
		assertThat(RepairCheck.decide(DamageType.WATER_LEAK, 0.0, 0.0, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.REPAIRED);
		assertThat(RepairCheck.decide(DamageType.POTHOLE, 0.0004, 0.05, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.REPAIRED);
		assertThat(RepairCheck.decide(DamageType.WATER_LEAK, 0.0, 0.0, muse(Judgement.REPAIRED, 0.5)))
				.isEqualTo(Outcome.INCONCLUSIVE);
	}

	@Test
	void brokenSidewalksAreJudgedByMuseOnly() {
		// SAM only finds the sidewalk itself, which is still there after the repair.
		assertThat(RepairCheck.decide(DamageType.DAMAGED_SIDEWALK, 0.07, 0.15, muse(Judgement.NOT_REPAIRED, 0.9)))
				.isEqualTo(Outcome.NOT_REPAIRED);
		assertThat(RepairCheck.decide(DamageType.DAMAGED_SIDEWALK, 0.07, 0.15, muse(Judgement.REPAIRED, 0.9)))
				.isEqualTo(Outcome.REPAIRED);
	}

	@Test
	void aJudgementSetAsideIsNotExplainedWithItsOwnReason() {
		var unconvincing = new RepairVerdict(Judgement.REPAIRED, 0.78, "La vía aparece sin hueco.");
		assertThat(RepairCheck.publicReason(Outcome.INCONCLUSIVE, unconvincing)).isEqualTo(RepairCheck.SET_ASIDE_REASON);
		assertThat(RepairCheck.publicReason(Outcome.REPAIRED, new RepairVerdict(Judgement.REPAIRED, 0.9, "Tapado.")))
				.isEqualTo("Tapado.");
		var differentPlace = new RepairVerdict(Judgement.INCONCLUSIVE, 0.9, "No es el mismo lugar.");
		assertThat(RepairCheck.publicReason(Outcome.INCONCLUSIVE, differentPlace)).isEqualTo("No es el mismo lugar.");
	}
}
