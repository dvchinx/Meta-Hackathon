package com.civicrepairs.application.port.in;

import java.util.UUID;

/** Who hears about an Issue's progress: whoever reported it, said "I saw it too", filed or verified it. */
public interface FollowIssueUseCase {

	void followWithAccount(UUID issueId, UUID userId);

	/** Stores the number encrypted, only for these notices. Skipped (and logged) when encryption is unavailable. */
	void followWithWhatsApp(UUID issueId, String phoneNumber);

	/** @return how many Issues the number stopped following */
	int stopWhatsApp(String phoneNumber);
}
