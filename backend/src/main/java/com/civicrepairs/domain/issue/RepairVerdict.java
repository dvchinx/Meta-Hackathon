package com.civicrepairs.domain.issue;

import java.util.Objects;

/**
 * Muse Spark's comparison of the original damage photo with a new one of the same place.
 *
 * @param reason one short sentence in Spanish, shown publicly next to the photos
 */
public record RepairVerdict(Judgement judgement, double confidence, String reason) {

	public enum Judgement {
		REPAIRED,
		NOT_REPAIRED,
		/** Different place, unusable photo, or not enough to tell. */
		INCONCLUSIVE
	}

	public RepairVerdict {
		Objects.requireNonNull(judgement, "judgement");
		Objects.requireNonNull(reason, "reason");
		if (confidence < 0 || confidence > 1) {
			throw new IllegalArgumentException("confidence out of [0, 1]: " + confidence);
		}
	}
}
