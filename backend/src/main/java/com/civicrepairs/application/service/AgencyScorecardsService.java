package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AgencyScorecard;
import com.civicrepairs.application.port.in.AgencyScorecardsUseCase;
import com.civicrepairs.application.port.out.AgencyScorecardReadPort;
import com.civicrepairs.domain.petition.ResponseDeadline;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
class AgencyScorecardsService implements AgencyScorecardsUseCase {

	private final AgencyScorecardReadPort scorecards;
	private final Clock clock;

	AgencyScorecardsService(AgencyScorecardReadPort scorecards, Clock clock) {
		this.scorecards = scorecards;
		this.clock = clock;
	}

	@Override
	public List<AgencyScorecard> scorecards() {
		Instant now = clock.instant();
		return scorecards.scorecards(now, now.atZone(ResponseDeadline.BOGOTA).toLocalDate());
	}
}
