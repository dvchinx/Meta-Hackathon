package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.issue.RepairVerdict.Judgement;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.shared.DamageType;

/**
 * Combines the two signals of a before/after check. Both must agree; anything else asks for another photo.
 *
 * <ul>
 * <li>Muse Spark must be confident ({@value #MIN_CONFIDENCE}) about REPAIRED or NOT_REPAIRED.</li>
 * <li>For damage that disappears when repaired, the SAM mask area must agree: at most {@value #REPAIRED_MAX_RATIO}
 * of the original area to count as repaired, at least that much to count as still damaged.</li>
 * </ul>
 */
public final class RepairCheck {

	public static final double MIN_CONFIDENCE = 0.7;
	public static final double REPAIRED_MAX_RATIO = 0.3;

	private RepairCheck() {
	}

	/**
	 * @param areaBefore share of the original photo covered by the damage
	 * @param areaAfter  share of the new photo covered by the damage
	 */
	public static Outcome decide(DamageType type, double areaBefore, double areaAfter, RepairVerdict verdict) {
		if (verdict.judgement() == Judgement.INCONCLUSIVE || verdict.confidence() < MIN_CONFIDENCE) {
			return Outcome.INCONCLUSIVE;
		}
		boolean repaired = verdict.judgement() == Judgement.REPAIRED;
		if (!type.areaShrinksWhenRepaired()) {
			return repaired ? Outcome.REPAIRED : Outcome.NOT_REPAIRED;
		}
		double ratio = areaRatio(areaBefore, areaAfter);
		if (repaired) {
			return ratio <= REPAIRED_MAX_RATIO ? Outcome.REPAIRED : Outcome.INCONCLUSIVE;
		}
		return ratio > REPAIRED_MAX_RATIO ? Outcome.NOT_REPAIRED : Outcome.INCONCLUSIVE;
	}

	/** After / before. With no damage measured before, any damage now counts as all of it. */
	static double areaRatio(double areaBefore, double areaAfter) {
		if (areaBefore <= 0) {
			return areaAfter > 0 ? 1 : 0;
		}
		return areaAfter / areaBefore;
	}
}
