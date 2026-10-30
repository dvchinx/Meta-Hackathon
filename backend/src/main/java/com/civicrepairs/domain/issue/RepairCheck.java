package com.civicrepairs.domain.issue;

import com.civicrepairs.domain.issue.RepairVerdict.Judgement;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.shared.DamageType;

/**
 * Combines the two signals of a before/after check. Both must agree; anything else asks for another photo.
 *
 * <ul>
 * <li>Muse Spark must be confident about REPAIRED ({@value #MIN_CONFIDENCE_REPAIRED}) or NOT_REPAIRED
 * ({@value #MIN_CONFIDENCE}). Closing an Issue by mistake is the costly error (it ends every notice and the case),
 * while a wrong reopening only asks the agency again, so a repair needs more certainty.</li>
 * <li>For damage that disappears when repaired, the SAM mask area must agree: at most {@value #REPAIRED_MAX_RATIO}
 * of the original area to count as repaired, at least that much to count as still damaged. When SAM found (almost)
 * nothing in the original photo there is no area to compare, and Muse decides alone, in both directions.</li>
 * </ul>
 */
public final class RepairCheck {

	public static final double MIN_CONFIDENCE = 0.7;
	public static final double MIN_CONFIDENCE_REPAIRED = 0.85;
	public static final double REPAIRED_MAX_RATIO = 0.3;
	/** Share of the photo below which the original mask is noise, not a measurement (a pothole is 0.8–2 %). */
	public static final double MIN_MEASURED_AREA = 0.001;
	static final String SET_ASIDE_REASON =
			"La foto no basta para confirmar el estado del daño: hace falta una desde el mismo punto de la original.";

	private RepairCheck() {
	}

	/**
	 * @param areaBefore share of the original photo covered by the damage
	 * @param areaAfter  share of the new photo covered by the damage
	 */
	public static Outcome decide(DamageType type, double areaBefore, double areaAfter, RepairVerdict verdict) {
		boolean repaired = verdict.judgement() == Judgement.REPAIRED;
		double required = repaired ? MIN_CONFIDENCE_REPAIRED : MIN_CONFIDENCE;
		if (verdict.judgement() == Judgement.INCONCLUSIVE || verdict.confidence() < required) {
			return Outcome.INCONCLUSIVE;
		}
		if (!type.areaShrinksWhenRepaired() || areaBefore < MIN_MEASURED_AREA) {
			return repaired ? Outcome.REPAIRED : Outcome.NOT_REPAIRED;
		}
		double ratio = areaAfter / areaBefore;
		if (repaired) {
			return ratio <= REPAIRED_MAX_RATIO ? Outcome.REPAIRED : Outcome.INCONCLUSIVE;
		}
		return ratio > REPAIRED_MAX_RATIO ? Outcome.NOT_REPAIRED : Outcome.INCONCLUSIVE;
	}

	/**
	 * The sentence shown next to the photos. When these rules set aside Muse Spark's judgement, its reason would
	 * contradict the outcome ("the hole is gone" on a photo that did not convince us), so a neutral one replaces it.
	 */
	public static String publicReason(Outcome outcome, RepairVerdict verdict) {
		if (outcome == Outcome.INCONCLUSIVE && verdict.judgement() != Judgement.INCONCLUSIVE) {
			return SET_ASIDE_REASON;
		}
		return verdict.reason();
	}
}
