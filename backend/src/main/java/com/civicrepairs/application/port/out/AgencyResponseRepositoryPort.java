package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.issue.AgencyResponse;

public interface AgencyResponseRepositoryPort {

	void save(AgencyResponse response);
}
