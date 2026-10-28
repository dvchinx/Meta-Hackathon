package com.civicrepairs.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * After telling a WhatsApp follower that the agency claims the repair, their next photo is the verification photo
 * for that Issue (until it expires). One pending Issue per person: the latest notice wins.
 */
public interface PendingVerificationPort {

	void await(String followerKey, UUID issueId, Instant expiresAt);

	Optional<UUID> find(String followerKey, Instant now);

	void clear(String followerKey);
}
