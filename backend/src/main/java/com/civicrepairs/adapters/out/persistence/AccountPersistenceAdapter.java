package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.EmailTokenRepositoryPort;
import com.civicrepairs.application.port.out.SessionRepositoryPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.EmailTokenPurpose;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class AccountPersistenceAdapter implements UserAccountRepositoryPort, SessionRepositoryPort, EmailTokenRepositoryPort {

	private final JdbcClient jdbc;
	private final Clock clock;

	AccountPersistenceAdapter(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return jdbc.sql("SELECT * FROM user_account WHERE id = :id").param("id", id).query(this::map).optional();
	}

	@Override
	public Optional<UserAccount> findByEmail(String email) {
		return jdbc.sql("SELECT * FROM user_account WHERE email = :email").param("email", email).query(this::map)
				.optional();
	}

	@Override
	public Optional<UserAccount> findByGoogleSubject(String subject) {
		return jdbc.sql("SELECT * FROM user_account WHERE google_subject = :sub").param("sub", subject).query(this::map)
				.optional();
	}

	@Override
	public void save(UserAccount a) {
		jdbc.sql("""
						INSERT INTO user_account (id, email, display_name, password_hash, email_verified_at, google_subject,
						                          created_at, updated_at)
						VALUES (:id, :email, :name, :hash, :verified, :google, :created, :now)
						ON CONFLICT (id) DO UPDATE SET
						    display_name = EXCLUDED.display_name, password_hash = EXCLUDED.password_hash,
						    email_verified_at = EXCLUDED.email_verified_at, google_subject = EXCLUDED.google_subject,
						    updated_at = EXCLUDED.updated_at
						""")
				.param("id", a.id())
				.param("email", a.email())
				.param("name", a.displayName())
				.param("hash", a.passwordHash().orElse(null))
				.param("verified", a.emailVerifiedAt().map(Timestamp::from).orElse(null))
				.param("google", a.googleSubject().orElse(null))
				.param("created", Timestamp.from(a.createdAt()))
				.param("now", Timestamp.from(clock.instant()))
				.update();
	}

	@Override
	public void create(String tokenHash, UUID userId, Instant expiresAt) {
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.sql("""
						INSERT INTO user_session (token_hash, user_id, created_at, expires_at, last_seen_at)
						VALUES (:hash, :user, :now, :expires, :now)
						""")
				.param("hash", tokenHash)
				.param("user", userId)
				.param("now", now)
				.param("expires", Timestamp.from(expiresAt))
				.update();
	}

	@Override
	public Optional<UUID> findUser(String tokenHash, Instant now) {
		return jdbc.sql("""
						UPDATE user_session SET last_seen_at = :now
						WHERE token_hash = :hash AND expires_at > :now
						RETURNING user_id
						""")
				.param("hash", tokenHash)
				.param("now", Timestamp.from(now))
				.query(UUID.class)
				.optional();
	}

	@Override
	public void delete(String tokenHash) {
		jdbc.sql("DELETE FROM user_session WHERE token_hash = :hash").param("hash", tokenHash).update();
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		jdbc.sql("DELETE FROM user_session WHERE user_id = :user").param("user", userId).update();
	}

	@Override
	public void store(String tokenHash, UUID userId, EmailTokenPurpose purpose, Instant expiresAt) {
		jdbc.sql("""
						INSERT INTO email_token (token_hash, user_id, purpose, expires_at) VALUES (:hash, :user, :purpose, :expires)
						""")
				.param("hash", tokenHash)
				.param("user", userId)
				.param("purpose", purpose.name())
				.param("expires", Timestamp.from(expiresAt))
				.update();
	}

	@Override
	public Optional<UUID> consume(String tokenHash, EmailTokenPurpose purpose, Instant now) {
		return jdbc.sql("""
						UPDATE email_token SET used_at = :now
						WHERE token_hash = :hash AND purpose = :purpose AND used_at IS NULL AND expires_at > :now
						RETURNING user_id
						""")
				.param("hash", tokenHash)
				.param("purpose", purpose.name())
				.param("now", Timestamp.from(now))
				.query(UUID.class)
				.optional();
	}

	private UserAccount map(ResultSet rs, int row) throws SQLException {
		Timestamp verified = rs.getTimestamp("email_verified_at");
		return UserAccount.rehydrate(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
				rs.getString("password_hash"), verified == null ? null : verified.toInstant(),
				rs.getString("google_subject"), rs.getTimestamp("created_at").toInstant());
	}
}
