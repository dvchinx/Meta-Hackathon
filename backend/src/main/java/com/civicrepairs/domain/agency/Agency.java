package com.civicrepairs.domain.agency;

import java.util.Objects;

/** Public body responsible for a kind of damage. Petitions are simulated: we never file them for real. */
public record Agency(String code, String name, String petitionEmail) {

	public Agency {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(name, "name");
	}
}
