package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.DamageType;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the citizen's own reports with the state of the damage it belongs to.
 *
 * @param issueId      null until the report is confirmed
 * @param issueStatus  null until the report is confirmed
 */
public record MyReport(
		UUID reportId,
		ReportStatus reportStatus,
		Instant createdAt,
		DamageType damageType,
		String summary,
		double latitude,
		double longitude,
		UUID issueId,
		IssueStatus issueStatus,
		int issueReportCount,
		UUID coverMediaId) {
}
