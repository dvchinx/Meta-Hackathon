package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.IssueFollowerPort;
import com.civicrepairs.application.port.out.NotificationOutboxPort;
import com.civicrepairs.application.port.out.PendingVerificationPort;
import com.civicrepairs.domain.issue.IssueStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Followers, the WhatsApp verification hand-off and the notification outbox. Numbers are encrypted at rest. */
@Component
class NotificationPersistenceAdapter implements IssueFollowerPort, PendingVerificationPort, NotificationOutboxPort {

	private final JdbcClient jdbc;
	private final PersonalDataCipher cipher;
	private final Clock clock;

	NotificationPersistenceAdapter(JdbcClient jdbc, PersonalDataCipher cipher, Clock clock) {
		this.jdbc = jdbc;
		this.cipher = cipher;
		this.clock = clock;
	}

	@Override
	public void followWithAccount(UUID issueId, String followerKey, UUID userId) {
		jdbc.sql("""
						INSERT INTO issue_follower (issue_id, follower_key, channel, user_id, created_at)
						VALUES (:issue, :key, 'WEB', :user, :now)
						ON CONFLICT (issue_id, follower_key) DO NOTHING
						""")
				.param("issue", issueId)
				.param("key", followerKey)
				.param("user", userId)
				.param("now", Timestamp.from(clock.instant()))
				.update();
	}

	@Override
	public void followWithWhatsApp(UUID issueId, String followerKey, String phoneNumber) {
		byte[] recipient = cipher.encrypt(phoneNumber.getBytes(StandardCharsets.UTF_8), context(issueId, followerKey));
		jdbc.sql("""
						INSERT INTO issue_follower (issue_id, follower_key, channel, recipient, created_at)
						VALUES (:issue, :key, 'WHATSAPP', :recipient, :now)
						ON CONFLICT (issue_id, follower_key) DO NOTHING
						""")
				.param("issue", issueId)
				.param("key", followerKey)
				.param("recipient", recipient)
				.param("now", Timestamp.from(clock.instant()))
				.update();
	}

	@Override
	public List<Follower> followers(UUID issueId) {
		return jdbc.sql("""
						SELECT f.follower_key, f.channel, f.recipient,
						       CASE WHEN u.email_verified_at IS NOT NULL THEN u.email END AS email
						FROM issue_follower f LEFT JOIN user_account u ON u.id = f.user_id
						WHERE f.issue_id = :issue
						ORDER BY f.created_at
						""")
				.param("issue", issueId)
				.query((rs, n) -> {
					String key = rs.getString("follower_key");
					byte[] recipient = rs.getBytes("recipient");
					String phone = recipient == null ? null
							: new String(cipher.decrypt(recipient, context(issueId, key)), StandardCharsets.UTF_8);
					return new Follower(key, Channel.valueOf(rs.getString("channel")), rs.getString("email"), phone);
				})
				.list();
	}

	@Override
	@Transactional
	public void forgetWhatsAppNumbers(UUID issueId) {
		jdbc.sql("DELETE FROM issue_follower WHERE issue_id = :issue AND channel = 'WHATSAPP'")
				.param("issue", issueId).update();
		jdbc.sql("DELETE FROM pending_verification WHERE issue_id = :issue").param("issue", issueId).update();
	}

	@Override
	public int unfollowAll(String followerKey) {
		return jdbc.sql("DELETE FROM issue_follower WHERE follower_key = :key").param("key", followerKey).update();
	}

	@Override
	public void await(String followerKey, UUID issueId, Instant expiresAt) {
		jdbc.sql("""
						INSERT INTO pending_verification (follower_key, issue_id, expires_at) VALUES (:key, :issue, :expires)
						ON CONFLICT (follower_key) DO UPDATE SET issue_id = EXCLUDED.issue_id, expires_at = EXCLUDED.expires_at
						""")
				.param("key", followerKey)
				.param("issue", issueId)
				.param("expires", Timestamp.from(expiresAt))
				.update();
	}

	@Override
	public Optional<UUID> find(String followerKey, Instant now) {
		return jdbc.sql("SELECT issue_id FROM pending_verification WHERE follower_key = :key AND expires_at > :now")
				.param("key", followerKey)
				.param("now", Timestamp.from(now))
				.query(UUID.class)
				.optional();
	}

	@Override
	public void clear(String followerKey) {
		jdbc.sql("DELETE FROM pending_verification WHERE follower_key = :key").param("key", followerKey).update();
	}

	@Override
	public List<StatusChange> pending(int limit) {
		return jdbc.sql("SELECT id, issue_id, to_status FROM notification_outbox WHERE sent_at IS NULL ORDER BY id LIMIT :n")
				.param("n", limit)
				.query((rs, n) -> new StatusChange(rs.getLong("id"), rs.getObject("issue_id", UUID.class),
						IssueStatus.valueOf(rs.getString("to_status"))))
				.list();
	}

	@Override
	public void markSent(long id) {
		jdbc.sql("UPDATE notification_outbox SET sent_at = :now WHERE id = :id")
				.param("now", Timestamp.from(clock.instant()))
				.param("id", id)
				.update();
	}

	private static String context(UUID issueId, String followerKey) {
		return "issue_follower:" + issueId + ":" + followerKey;
	}
}
