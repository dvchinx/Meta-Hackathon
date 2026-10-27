package com.civicrepairs.application.port.in;

import java.util.UUID;

/** The citizen accepts or rejects the summary. Accepting creates an Issue or joins an existing one. */
public interface ConfirmReportUseCase {

	/** @throws ReportNotFoundException if the report does not exist or belongs to another reporter */
	ConfirmationResult confirm(UUID reportId, String reporterId, boolean accepted);

	/**
	 * @param issueId     null when the report was cancelled
	 * @param newIssue    false when the report joined an existing Issue (duplicate)
	 */
	record ConfirmationResult(UUID reportId, boolean accepted, UUID issueId, boolean newIssue, int reportCount) {
	}
}
