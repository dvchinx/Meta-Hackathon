package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.shared.DamageType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the admin panel shows: credits, the state of the map and the manual review queue. */
public record AdminOverview(boolean metaEnabled, BigDecimal spentUsd, BigDecimal budgetUsd,
		Map<IssueStatus, Integer> issuesByStatus, List<ReviewItem> manualReview) {

	/** @param damageType null when the models never answered */
	public record ReviewItem(UUID reportId, ReportChannel channel, Instant createdAt, DamageType damageType,
			String summary, String reason) {
	}
}
