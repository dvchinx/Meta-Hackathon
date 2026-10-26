package com.civicrepairs.domain.shared;

public enum DamageType {
	POTHOLE,
	OPEN_MANHOLE,
	DAMAGED_POLE,
	RISKY_TREE,
	/** Anything outside the MVP scope. Always goes to manual review. */
	OTHER;

	public boolean requiresManualReview() {
		return this == OTHER;
	}
}
