package com.civicrepairs.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Who gets news about an Issue. One row per person ({@code followerKey} is their salted reporter hash). WhatsApp
 * numbers are stored encrypted, only for these notices, and forgotten once the Issue is verified; implementations
 * throw {@link PersonalDataUnavailableException} when they cannot encrypt.
 */
public interface IssueFollowerPort {

	void followWithAccount(UUID issueId, String followerKey, UUID userId);

	void followWithWhatsApp(UUID issueId, String followerKey, String phoneNumber);

	/** Web followers come with their verified email; WhatsApp followers with their number. */
	List<Follower> followers(UUID issueId);

	/** Deletes the stored WhatsApp numbers of an Issue, and their pending verifications (once it is verified). */
	void forgetWhatsAppNumbers(UUID issueId);

	/** "BAJA": stops every notice to this person. @return how many Issues they followed */
	int unfollowAll(String followerKey);

	enum Channel {
		WEB,
		WHATSAPP
	}

	/**
	 * @param email       verified email of a web follower; null for WhatsApp or unverified accounts
	 * @param phoneNumber WhatsApp number; null for web
	 */
	record Follower(String followerKey, Channel channel, String email, String phoneNumber) {
	}
}
