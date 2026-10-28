package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.issue.AgencyResponse;
import com.civicrepairs.domain.issue.RepairVerdict;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.shared.DamageType;

/** Muse Spark. Implementations cache by media hash + prompt version and record the cost of every call. */
public interface LanguageModelPort {

	/** @throws ModelUnavailableException on invalid output (after one retry), errors or exhausted budget */
	Interpretation interpret(MediaContent photo, String context);

	/** Whether two (blurred) photos show the same physical damage. Used for deduplication. */
	SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate);

	/** Reads an agency's reply: does it claim the repair is done? A claim never closes the Issue by itself. */
	AgencyResponse.Reading classifyAgencyResponse(String body);

	/** Compares the original (blurred) damage photo with a new (blurred) one of the same place. */
	RepairVerdict judgeRepair(MediaContent before, MediaContent after, DamageType damageType);

	record SameDamageVerdict(boolean sameDamage, double confidence) {
	}
}
