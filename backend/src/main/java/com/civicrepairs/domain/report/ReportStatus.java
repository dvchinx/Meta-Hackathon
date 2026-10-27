package com.civicrepairs.domain.report;

/**
 * <pre>
 * COLLECTING → ANALYZING → AWAITING_CONFIRMATION → CONFIRMED
 *     ↑            │                 ↘ CANCELLED
 *     └────────────┘ photo rejected (not urban damage or low confidence): ask for another photo
 * ANALYZING → MANUAL_REVIEW (OTHER damage, invalid model output twice, or budget exhausted)
 * </pre>
 */
public enum ReportStatus {
	COLLECTING,
	ANALYZING,
	AWAITING_CONFIRMATION,
	CONFIRMED,
	CANCELLED,
	MANUAL_REVIEW;

	public boolean isOpen() {
		return this == COLLECTING || this == ANALYZING || this == AWAITING_CONFIRMATION;
	}
}
