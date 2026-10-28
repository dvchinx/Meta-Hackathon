package com.civicrepairs.domain.petition;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PetitionTest {

	private static final Petitioner ANA = new Petitioner(
			new FilerProfile(IdentificationType.CC, "1020304050", "Ana", "Ruiz", "3001234567", null), "ana@example.com");

	@Test
	void numbersAndDatesUseBogotasCalendar() {
		// 03:00 UTC on Oct 28 is still Oct 27 in Bogotá.
		var p = Petition.file(UUID.randomUUID(), UUID.randomUUID(), "UMV", UUID.randomUUID(), 42,
				Instant.parse("2026-10-28T03:00:00Z"), ANA, "Hueco", "2026-10-27");

		assertThat(p.radicado()).isEqualTo("SIM-2026-000042");
		assertThat(p.dueDate()).isEqualTo(LocalDate.of(2026, 11, 19));
	}
}
