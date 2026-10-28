package com.civicrepairs.domain.petition;

/** Identity documents accepted by Bogotá's petition system (SDQS) for natural persons. */
public enum IdentificationType {
	/** Cédula de ciudadanía. */
	CC(true),
	/** Cédula de extranjería. */
	CE(true),
	/** Tarjeta de identidad. */
	TI(true),
	/** Pasaporte. */
	PA(false),
	/** Permiso especial de permanencia. */
	PEP(false),
	/** Permiso por protección temporal. */
	PPT(false);

	private final boolean numeric;

	IdentificationType(boolean numeric) {
		this.numeric = numeric;
	}

	public boolean isNumeric() {
		return numeric;
	}
}
