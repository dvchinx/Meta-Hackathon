package com.civicrepairs.domain.shared;

import java.util.Objects;

/** One of the 20 localidades of Bogotá. Each has its own Alcaldía Local. */
public record Locality(String code, String name) {

	public Locality {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(name, "name");
	}
}
