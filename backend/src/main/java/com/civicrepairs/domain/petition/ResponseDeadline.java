package com.civicrepairs.domain.petition;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Legal time to answer a petition: 15 business days counted from the day after filing (Ley 1755 de 2015, art. 14).
 * Dates are in Bogotá's time zone.
 */
public final class ResponseDeadline {

	public static final int BUSINESS_DAYS = 15;
	public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private ResponseDeadline() {
	}

	/** Last day the agency can answer on time. */
	public static LocalDate dueDate(LocalDate filedOn) {
		LocalDate day = Objects.requireNonNull(filedOn, "filedOn");
		int counted = 0;
		while (counted < BUSINESS_DAYS) {
			day = day.plusDays(1);
			if (ColombianHolidays.isBusinessDay(day)) {
				counted++;
			}
		}
		return day;
	}

	/** Business days after {@code today} up to the due date: 0 on the due date itself and once it has passed. */
	public static int businessDaysLeft(LocalDate dueDate, LocalDate today) {
		int left = 0;
		for (LocalDate day = today.plusDays(1); !day.isAfter(dueDate); day = day.plusDays(1)) {
			if (ColombianHolidays.isBusinessDay(day)) {
				left++;
			}
		}
		return left;
	}

	public static boolean isOverdue(LocalDate dueDate, LocalDate today) {
		return today.isAfter(dueDate);
	}
}
