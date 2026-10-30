package com.civicrepairs.application.port.in;

import java.util.List;

/** Public accountability: every agency with at least one Issue, the ones with most overdue petitions first. */
public interface AgencyScorecardsUseCase {

	List<AgencyScorecard> scorecards();
}
