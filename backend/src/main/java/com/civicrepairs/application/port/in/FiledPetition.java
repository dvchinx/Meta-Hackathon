package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.petition.ResponseDeadline;

import java.time.Instant;
import java.time.LocalDate;

/** Public facts about the latest petition of an Issue. Never who filed it. */
public record FiledPetition(String radicado, Instant filedAt, LocalDate dueDate) {

	public int businessDaysLeft(LocalDate today) {
		return ResponseDeadline.businessDaysLeft(dueDate, today);
	}

	public boolean isOverdue(LocalDate today) {
		return ResponseDeadline.isOverdue(dueDate, today);
	}
}
