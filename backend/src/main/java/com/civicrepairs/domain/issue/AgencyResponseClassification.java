package com.civicrepairs.domain.issue;

/** How Muse Spark reads an agency's reply. */
public enum AgencyResponseClassification {
	/** The agency says it repaired the damage. That is a claim, not a verification. */
	RESOLVES,
	EVASIVE,
	NO_RESPONSE
}
