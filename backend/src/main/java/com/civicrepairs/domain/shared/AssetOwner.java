package com.civicrepairs.domain.shared;

/**
 * Whose infrastructure the damaged asset is, read from the markings on lids and poles ("EAAB", "Codensa", "Vanti",
 * "ETB"...). Manholes and poles look alike across utilities, so this decides the responsible agency.
 */
public enum AssetOwner {
	WATER,
	POWER,
	GAS,
	TELECOM,
	STREET_LIGHTING,
	UNKNOWN
}
