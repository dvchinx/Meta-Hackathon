package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AgencyScorecard;
import com.civicrepairs.application.port.in.AgencyScorecardsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public accountability page: how each agency answers petitions and whether its repairs hold up to a photo. */
@RestController
@RequestMapping("/api/agencies")
class AgencyController {

	private final AgencyScorecardsUseCase scorecards;

	AgencyController(AgencyScorecardsUseCase scorecards) {
		this.scorecards = scorecards;
	}

	@GetMapping("/scorecards")
	List<AgencyScorecard> scorecards() {
		return scorecards.scorecards();
	}
}
