package com.civicrepairs.domain.shared;

public enum DamageType {
	POTHOLE,
	OPEN_MANHOLE,
	DAMAGED_POLE,
	RISKY_TREE,
	STREETLIGHT_OUT,
	WATER_LEAK,
	BLOCKED_DRAIN,
	/** Garbage or rubble dumped in public space ("basuras y escombros"). */
	GARBAGE_DUMP,
	DAMAGED_SIDEWALK,
	/** Benches, bollards, railings, bus shelters and similar. */
	DAMAGED_STREET_FURNITURE,
	/** Anything outside the supported types. Always goes to manual review. */
	OTHER;

	public boolean requiresManualReview() {
		return this == OTHER;
	}

	/** Damage that is an emergency when urgent: the citizen is told to call 123 before anything else. */
	public boolean isEmergencyWhenUrgent() {
		return switch (this) {
			case OPEN_MANHOLE, DAMAGED_POLE, RISKY_TREE -> true;
			default -> false;
		};
	}
}
