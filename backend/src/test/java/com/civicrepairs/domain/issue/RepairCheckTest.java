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
	void withNoDamageMeasuredBeforeAnyDamageNowCountsAsAllOfIt() {
		assertThat(RepairCheck.areaRatio(0, 0)).isZero();
		assertThat(RepairCheck.areaRatio(0, 0.05)).isEqualTo(1);
		assertThat(RepairCheck.areaRatio(0.2, 0.05)).isEqualTo(0.25);
	}
}
