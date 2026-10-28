package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.FiledPetition;
import com.civicrepairs.domain.petition.ResponseDeadline;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Public state of the latest petition: radicado and the legal deadline, counted in Bogotá business days as of today.
 *
 * @param businessDaysLeft 0 on the due date and after it
 */
record PetitionStatusResponse(String radicado, Instant filedAt, LocalDate dueDate, int businessDaysLeft,
		boolean overdue) {

	static PetitionStatusResponse from(FiledPetition petition, Clock clock) {
		if (petition == null) {
			return null;
		}
		LocalDate today = LocalDate.ofInstant(clock.instant(), ResponseDeadline.BOGOTA);
		return new PetitionStatusResponse(petition.radicado(), petition.filedAt(), petition.dueDate(),
				petition.businessDaysLeft(today), petition.isOverdue(today));
	}
}
