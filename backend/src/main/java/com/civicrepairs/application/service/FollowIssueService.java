package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.FollowIssueUseCase;
import com.civicrepairs.application.port.out.IssueFollowerPort;
import com.civicrepairs.application.port.out.PendingVerificationPort;
import com.civicrepairs.application.port.out.PersonalDataUnavailableException;
import com.civicrepairs.domain.report.ReportChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class FollowIssueService implements FollowIssueUseCase {

	private static final Logger log = LoggerFactory.getLogger(FollowIssueService.class);

	private final IssueFollowerPort followers;
	private final PendingVerificationPort pending;
	private final ReporterIds reporterIds;

	FollowIssueService(IssueFollowerPort followers, PendingVerificationPort pending, ReporterIds reporterIds) {
		this.followers = followers;
		this.pending = pending;
		this.reporterIds = reporterIds;
	}

	@Override
	public void followWithAccount(UUID issueId, UUID userId) {
		followers.followWithAccount(issueId, reporterIds.forUser(userId), userId);
	}

	@Override
	public void followWithWhatsApp(UUID issueId, String phoneNumber) {
		try {
			followers.followWithWhatsApp(issueId, reporterIds.hash(ReportChannel.WHATSAPP, phoneNumber), phoneNumber);
		} catch (PersonalDataUnavailableException e) {
			// The report itself is fine; this citizen just will not get notices.
			log.warn("Cannot store the WhatsApp follower of Issue {}: {}", issueId, e.getMessage());
		}
	}

	@Override
	@Transactional
	public int stopWhatsApp(String phoneNumber) {
		String key = reporterIds.hash(ReportChannel.WHATSAPP, phoneNumber);
		pending.clear(key);
		return followers.unfollowAll(key);
	}
}
