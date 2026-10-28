package com.civicrepairs.domain.account;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A citizen's web account. Only the minimum: a display name and a way to sign in. Nothing here identifies the
 * person beyond what they chose to share.
 */
public final class UserAccount {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	public static final int MAX_DISPLAY_NAME = 60;

	private final UUID id;
	private final String email;
	private String displayName;
	private String passwordHash;
	private Instant emailVerifiedAt;
	private String googleSubject;
	private final Instant createdAt;

	private UserAccount(UUID id, String email, String displayName, String passwordHash, Instant emailVerifiedAt,
			String googleSubject, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.email = Objects.requireNonNull(email, "email");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.passwordHash = passwordHash;
		this.emailVerifiedAt = emailVerifiedAt;
		this.googleSubject = googleSubject;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public static UserAccount register(UUID id, String email, String displayName, String passwordHash, Instant at) {
		return new UserAccount(id, normalizeEmail(email), normalizeDisplayName(displayName),
				Objects.requireNonNull(passwordHash, "passwordHash"), null, null, at);
	}

	/** Google already verified the address: the account starts verified and without a password. */
	public static UserAccount registerWithGoogle(UUID id, String email, String displayName, String googleSubject,
			Instant at) {
		return new UserAccount(id, normalizeEmail(email), normalizeDisplayName(displayName), null, at,
				Objects.requireNonNull(googleSubject, "googleSubject"), at);
	}

	public static UserAccount rehydrate(UUID id, String email, String displayName, String passwordHash,
			Instant emailVerifiedAt, String googleSubject, Instant createdAt) {
		return new UserAccount(id, email, displayName, passwordHash, emailVerifiedAt, googleSubject, createdAt);
	}

	/** @throws IllegalArgumentException when the address is not plausible */
	public static String normalizeEmail(String email) {
		String normalized = Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
		if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
			throw new IllegalArgumentException("invalid email address");
		}
		return normalized;
	}

	public static String normalizeDisplayName(String name) {
		String normalized = Objects.requireNonNull(name, "displayName").strip().replaceAll("\\s+", " ");
		if (normalized.length() < 2 || normalized.length() > MAX_DISPLAY_NAME) {
			throw new IllegalArgumentException("display name must have 2 to " + MAX_DISPLAY_NAME + " characters");
		}
		return normalized;
	}

	public void verifyEmail(Instant at) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = Objects.requireNonNull(at, "at");
		}
	}

	/**
	 * The owner of the address signed in with Google. If the address was never verified, whoever set the current
	 * password did not prove they own it, so that password is discarded.
	 *
	 * @return true when an unverified password was discarded (the caller must also end its sessions)
	 */
	public boolean linkGoogle(String subject, Instant at) {
		this.googleSubject = Objects.requireNonNull(subject, "subject");
		boolean discarded = emailVerifiedAt == null && passwordHash != null;
		if (discarded) {
			passwordHash = null;
		}
		verifyEmail(at);
		return discarded;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public UUID id() {
		return id;
	}

	public String email() {
		return email;
	}

	public String displayName() {
		return displayName;
	}

	public Optional<String> passwordHash() {
		return Optional.ofNullable(passwordHash);
	}

	public Optional<Instant> emailVerifiedAt() {
		return Optional.ofNullable(emailVerifiedAt);
	}

	public Optional<String> googleSubject() {
		return Optional.ofNullable(googleSubject);
	}

	public Instant createdAt() {
		return createdAt;
	}
}
