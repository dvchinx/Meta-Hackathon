package com.civicrepairs.domain.petition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ColombianHolidaysTest {

	@Test
	void matchesThe2026Calendar() {
		assertThat(ColombianHolidays.of(2026)).containsExactly(
				LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 12),
				LocalDate.of(2026, 3, 23),
				LocalDate.of(2026, 4, 2),
				LocalDate.of(2026, 4, 3),
				LocalDate.of(2026, 5, 1),
				LocalDate.of(2026, 5, 18),
				LocalDate.of(2026, 6, 8),
				LocalDate.of(2026, 6, 15),
				LocalDate.of(2026, 6, 29),
				LocalDate.of(2026, 7, 20),
				LocalDate.of(2026, 8, 7),
				LocalDate.of(2026, 8, 17),
				LocalDate.of(2026, 10, 12),
				LocalDate.of(2026, 11, 2),
				LocalDate.of(2026, 11, 16),
				LocalDate.of(2026, 12, 8),
				LocalDate.of(2026, 12, 25));
	}

	@Test
	void computesEaster() {
		assertThat(ColombianHolidays.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
		assertThat(ColombianHolidays.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
	}

	@Test
	void weekendsAndHolidaysAreNotBusinessDays() {
		assertThat(ColombianHolidays.isBusinessDay(LocalDate.of(2026, 10, 27))).isTrue();
		assertThat(ColombianHolidays.isBusinessDay(LocalDate.of(2026, 10, 31))).isFalse();
		assertThat(ColombianHolidays.isBusinessDay(LocalDate.of(2026, 11, 2))).isFalse();
	}
}
