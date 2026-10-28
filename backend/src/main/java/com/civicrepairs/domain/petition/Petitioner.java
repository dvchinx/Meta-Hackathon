package com.civicrepairs.domain.petition;

import java.util.Objects;

/** Snapshot of who filed a petition, as shared with the agency at that moment. */
public record Petitioner(FilerProfile profile, String email) {

	public Petitioner {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(email, "email");
	}
}
