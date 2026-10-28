package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.IssueHistory;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.out.EmailPort;
import com.civicrepairs.application.port.out.IssueFollowerPort;
import com.civicrepairs.application.port.out.IssueFollowerPort.Channel;
import com.civicrepairs.application.port.out.IssueFollowerPort.Follower;
import com.civicrepairs.application.port.out.IssueHistoryReadPort;
import com.civicrepairs.application.port.out.MessagingPort;
import com.civicrepairs.application.port.out.NotificationOutboxPort;
import com.civicrepairs.application.port.out.NotificationOutboxPort.StatusChange;
import com.civicrepairs.application.port.out.PendingVerificationPort;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import com.civicrepairs.domain.issue.IssueStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Delivers the outbox: every status change is told to the Issue's followers. A change that is already stale (the
 * Issue moved on, e.g. AGENCY_RESPONDED followed at once by REPAIR_CLAIMED) is skipped: the later one speaks for
 * it. Sending is best effort; a change is never retried, so nobody gets the same notice twice.
 */
@Service
public class IssueNotifier {

	private static final Logger log = LoggerFactory.getLogger(IssueNotifier.class);
	private static final int BATCH = 50;
	/** How long a WhatsApp follower's next photo counts as the verification photo. */
	static final Duration VERIFICATION_WINDOW = Duration.ofDays(7);

	private final NotificationOutboxPort outbox;
	private final PublicIssueReadPort issues;
	private final IssueHistoryReadPort history;
	private final IssueFollowerPort followers;
	private final PendingVerificationPort pending;
	private final EmailPort email;
	private final MessagingPort messaging;
	private final Clock clock;
	private final String baseUrl;

	IssueNotifier(NotificationOutboxPort outbox, PublicIssueReadPort issues, IssueHistoryReadPort history,
			IssueFollowerPort followers, PendingVerificationPort pending, EmailPort email, MessagingPort messaging,
			Clock clock, @Value("${civic.public-base-url}") String publicBaseUrl) {
		this.outbox = outbox;
		this.issues = issues;
		this.history = history;
		this.followers = followers;
		this.pending = pending;
		this.email = email;
		this.messaging = messaging;
		this.clock = clock;
		this.baseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
				: publicBaseUrl;
	}

	@Scheduled(fixedDelayString = "${civic.notifications.poll-interval}",
			initialDelayString = "${civic.notifications.poll-interval}")
	public void dispatchPending() {
		for (StatusChange change : outbox.pending(BATCH)) {
			try {
				dispatch(change);
			} catch (RuntimeException e) {
				log.error("Notice for Issue {} ({}) failed", change.issueId(), change.status(), e);
			}
			outbox.markSent(change.id());
		}
	}

	private void dispatch(StatusChange change) {
		Optional<PublicIssue> found = issues.findPublic(change.issueId());
		if (found.isEmpty() || found.get().status() != change.status()) {
			return;
		}
		PublicIssue issue = found.get();
		String link = baseUrl + "/#/dano?id=" + issue.id();
		var notice = IssueNotices.forStatus(change.status(), issue, latestAgencySummary(change), link);
		if (notice.isEmpty()) {
			return;
		}
		boolean awaitPhoto = change.status() == IssueStatus.REPAIR_CLAIMED
				|| change.status() == IssueStatus.NEEDS_MORE_EVIDENCE;
		for (Follower follower : followers.followers(issue.id())) {
			if (follower.channel() == Channel.WEB && follower.email() != null) {
				var n = notice.get();
				email.send(follower.email(), n.subject(), n.text() + "\n\n" + link,
						EmailLayout.html(n.subject(), EmailLayout.escape(n.text()), link, n.action(),
								"Recibes este correo porque reportaste o sigues este daño."));
			} else if (follower.channel() == Channel.WHATSAPP && follower.phoneNumber() != null) {
				messaging.sendTemplate(follower.phoneNumber(), notice.get().template(), notice.get().params());
				if (awaitPhoto) {
					pending.await(follower.followerKey(), issue.id(), clock.instant().plus(VERIFICATION_WINDOW));
				}
			}
		}
		if (change.status() == IssueStatus.VERIFIED) {
			followers.forgetWhatsAppNumbers(issue.id());
		}
	}

	private String latestAgencySummary(StatusChange change) {
		if (change.status() != IssueStatus.AGENCY_RESPONDED) {
			return null;
		}
		List<IssueHistory.Response> responses = history.history(change.issueId()).agencyResponses();
		return responses.isEmpty() ? null : responses.getLast().summary();
	}
}
