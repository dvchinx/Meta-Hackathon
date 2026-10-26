package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;

import java.time.Instant;
import java.util.UUID;

/** What the public map may know about an Issue. Never contains reporter data. */
public record PublicIssue(
		UUID id,
		DamageType damageType,
		IssueStatus status,
		double latitude,
		double longitude,
		int reportCount,
		Instant openedAt,
		String agencyName,
		UUID coverMediaId) {
}
