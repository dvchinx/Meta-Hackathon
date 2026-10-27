package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.report.Interpretation;

/** Muse Spark. Implementations cache by media hash + prompt version and record the cost of every call. */
public interface LanguageModelPort {

	/** @throws ModelUnavailableException on invalid output (after one retry), errors or exhausted budget */
	Interpretation interpret(MediaContent photo, String context);

	/** Whether two (blurred) photos show the same physical damage. Used for deduplication. */
	SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate);

	record SameDamageVerdict(boolean sameDamage, double confidence) {
	}
}
