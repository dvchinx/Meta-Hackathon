package com.civicrepairs.domain.report;

public enum AnalysisOutcome {
	/** Waiting for the citizen to confirm the summary. */
	AWAITING_CONFIRMATION,
	/** Not urban damage or too uncertain: ask for another photo. Location and context are kept. */
	PHOTO_REJECTED,
	/** Valid but outside the four MVP damage types. */
	MANUAL_REVIEW
}
