package com.civicrepairs.application.port.in;

import java.util.UUID;

/** "I saw it too": joins an existing Issue without a new photo. */
public interface EndorseIssueUseCase {

	EndorsementResult endorse(UUID issueId, UUID userId);

	/** @param counted false when this citizen was already counted for the Issue */
	record EndorsementResult(UUID issueId, boolean counted, int reportCount) {
	}
}
