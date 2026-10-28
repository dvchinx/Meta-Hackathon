package com.civicrepairs.domain.report;

import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;

import java.util.List;
import java.util.Objects;

/**
 * Muse Spark's reading of a report, already validated against the schema.
 *
 * @param assetOwner whose lid or pole it is, from its markings; {@link AssetOwner#UNKNOWN} when unreadable (and for
 *                   reports interpreted before the field existed)
 */
public record Interpretation(
		DamageType damageType,
		Urgency urgency,
		boolean validUrbanDamage,
		List<String> locationHints,
		String summary,
		double confidence,
		AssetOwner assetOwner) {

	/** Below this, the bot asks for another photo instead of registering the report. */
	public static final double MIN_CONFIDENCE = 0.6;

	public Interpretation {
		Objects.requireNonNull(damageType, "damageType");
		Objects.requireNonNull(urgency, "urgency");
		Objects.requireNonNull(summary, "summary");
		locationHints = List.copyOf(locationHints);
		if (confidence < 0 || confidence > 1) {
			throw new IllegalArgumentException("confidence out of [0, 1]: " + confidence);
		}
		assetOwner = assetOwner == null ? AssetOwner.UNKNOWN : assetOwner;
	}

	public boolean isAcceptable() {
		return validUrbanDamage && confidence >= MIN_CONFIDENCE;
	}

	/** Immediate danger: before anything else, the citizen is told to call the 123 emergency line. */
	public boolean requiresEmergencyCall() {
		return urgency == Urgency.HIGH && damageType.isEmergencyWhenUrgent();
	}
}
