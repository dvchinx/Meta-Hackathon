package com.civicrepairs.application.port.in;

import java.util.UUID;

/** Also thrown when the report belongs to someone else, so callers cannot probe for other people's reports. */
public class ReportNotFoundException extends RuntimeException {

	public ReportNotFoundException(UUID reportId) {
		super("report not found: " + reportId);
	}
}
