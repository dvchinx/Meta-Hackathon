package com.civicrepairs.domain.petition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseDeadlineTest {

	private static final LocalDate FILED = LocalDate.of(2026, 10, 27);
	// Oct 28–30, Nov 3–6, 9–13, 17–19: Nov 2 and Nov 16 are holidays.
	private static final LocalDate DUE = LocalDate.of(2026, 11, 19);

	@Test
	void fifteenBusinessDaysFromTheDayAfterFiling() {
		assertThat(ResponseDeadline.dueDate(FILED)).isEqualTo(DUE);
	}

	@Test
	void filingOnAFridayStartsCountingOnMonday() {
		// Oct 30 is a Friday: Nov 3–6, 9–13, 17–20, 23–24.
		assertThat(ResponseDeadline.dueDate(LocalDate.of(2026, 10, 30))).isEqualTo(LocalDate.of(2026, 11, 24));
	}

	@Test
	void countsTheBusinessDaysLeft() {
		assertThat(ResponseDeadline.businessDaysLeft(DUE, FILED)).isEqualTo(15);
		assertThat(ResponseDeadline.businessDaysLeft(DUE, LocalDate.of(2026, 11, 18))).isEqualTo(1);
		assertThat(ResponseDeadline.businessDaysLeft(DUE, DUE)).isZero();
		assertThat(ResponseDeadline.businessDaysLeft(DUE, LocalDate.of(2026, 11, 25))).isZero();
	}

	@Test
	void overdueOnlyAfterTheDueDate() {
		assertThat(ResponseDeadline.isOverdue(DUE, DUE)).isFalse();
		assertThat(ResponseDeadline.isOverdue(DUE, DUE.plusDays(1))).isTrue();
	}
}
