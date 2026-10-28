package com.civicrepairs.domain.petition;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.TreeSet;

/**
 * Public holidays in Colombia. Most move to the following Monday (Ley 51 de 1983, "Ley Emiliani"); four depend on
 * Easter.
 */
public final class ColombianHolidays {

	private ColombianHolidays() {
	}

	public static Set<LocalDate> of(int year) {
		Set<LocalDate> days = new TreeSet<>();
		days.add(LocalDate.of(year, Month.JANUARY, 1));
		days.add(LocalDate.of(year, Month.MAY, 1));
		days.add(LocalDate.of(year, Month.JULY, 20));
		days.add(LocalDate.of(year, Month.AUGUST, 7));
		days.add(LocalDate.of(year, Month.DECEMBER, 8));
		days.add(LocalDate.of(year, Month.DECEMBER, 25));

		days.add(nextMonday(LocalDate.of(year, Month.JANUARY, 6)));
		days.add(nextMonday(LocalDate.of(year, Month.MARCH, 19)));
		days.add(nextMonday(LocalDate.of(year, Month.JUNE, 29)));
		days.add(nextMonday(LocalDate.of(year, Month.AUGUST, 15)));
		days.add(nextMonday(LocalDate.of(year, Month.OCTOBER, 12)));
		days.add(nextMonday(LocalDate.of(year, Month.NOVEMBER, 1)));
		days.add(nextMonday(LocalDate.of(year, Month.NOVEMBER, 11)));

		LocalDate easter = easterSunday(year);
		days.add(easter.minusDays(3)); // Jueves Santo
		days.add(easter.minusDays(2)); // Viernes Santo
		days.add(easter.plusDays(43)); // Ascensión (Thursday + 39, moved to Monday)
		days.add(easter.plusDays(64)); // Corpus Christi (Thursday + 60, moved to Monday)
		days.add(easter.plusDays(71)); // Sagrado Corazón (Friday + 68, moved to Monday)
		return days;
	}

	public static boolean isBusinessDay(LocalDate day) {
		DayOfWeek dow = day.getDayOfWeek();
		return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !of(day.getYear()).contains(day);
	}

	private static LocalDate nextMonday(LocalDate day) {
		return day.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	/** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
	static LocalDate easterSunday(int year) {
		int a = year % 19;
		int b = year / 100;
		int c = year % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int month = (h + l - 7 * m + 114) / 31;
		int day = (h + l - 7 * m + 114) % 31 + 1;
		return LocalDate.of(year, month, day);
	}
}
