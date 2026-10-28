package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.IssueStatus;

import java.util.UUID;

/** The admin panel (ADMIN_TOKEN). Agencies are simulated: their replies are typed here. */
public interface AdminUseCase {

	AdminOverview overview();

	/**
	 * Muse Spark reads the reply. A RESOLVES reply moves the Issue to REPAIR_CLAIMED, never further: only a photo
	 * closes it.
	 *
	 * @throws IssueActionException NOT_FOUND, or WRONG_STATUS unless the Issue is FILED
	 */
	AgencyResponseResult registerAgencyResponse(UUID issueId, String body);

	record AgencyResponseResult(UUID issueId, AgencyResponseClassification classification, String summary,
			IssueStatus status) {
	}
}
