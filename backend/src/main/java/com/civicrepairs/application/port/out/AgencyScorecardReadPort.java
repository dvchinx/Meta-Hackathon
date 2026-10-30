package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.AgencyScorecard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface AgencyScorecardReadPort {

	/**
	 * @param today Bogotá's date, to tell overdue petitions apart
	 */
	List<AgencyScorecard> scorecards(Instant now, LocalDate today);
}
