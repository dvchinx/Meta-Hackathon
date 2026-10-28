package com.civicrepairs.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {

	private static final Instant T0 = Instant.parse("2026-10-27T15:00:00Z");

	@Test
	void normalizesEmailAndDisplayName() {
		var account = UserAccount.register(UUID.randomUUID(), "  Ana.Maria@Example.COM ", "  Ana   María  ", "hash", T0);

		assertThat(account.email()).isEqualTo("ana.maria@example.com");
		assertThat(account.displayName()).isEqualTo("Ana María");
		assertThat(account.isEmailVerified()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "ana", "ana@", "@example.com", "ana @example.com", "ana@example"})
	void rejectsImplausibleEmails(String email) {
		assertThatThrownBy(() -> UserAccount.normalizeEmail(email)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "A"})
	void rejectsTooShortNames(String name) {
		assertThatThrownBy(() -> UserAccount.normalizeDisplayName(name)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void verificationHappensOnce() {
		var account = UserAccount.register(UUID.randomUUID(), "a@example.com", "Ana", "hash", T0);

		account.verifyEmail(T0.plusSeconds(60));
		account.verifyEmail(T0.plusSeconds(120));

		assertThat(account.emailVerifiedAt()).contains(T0.plusSeconds(60));
	}

	@Test
	void googleAccountsStartVerifiedWithoutPassword() {
		var account = UserAccount.registerWithGoogle(UUID.randomUUID(), "Ana@Gmail.com", "Ana", "g-1", T0);

		assertThat(account.isEmailVerified()).isTrue();
		assertThat(account.passwordHash()).isEmpty();
		assertThat(account.googleSubject()).contains("g-1");
	}

	@Test
	void linkingGoogleDiscardsAnUnprovenPassword() {
		var unverified = UserAccount.register(UUID.randomUUID(), "a@example.com", "Ana", "hash", T0);
		var verified = UserAccount.register(UUID.randomUUID(), "b@example.com", "Beto", "hash", T0);
		verified.verifyEmail(T0);

		assertThat(unverified.linkGoogle("g-a", T0)).isTrue();
		assertThat(unverified.passwordHash()).isEmpty();
		assertThat(unverified.isEmailVerified()).isTrue();

		assertThat(verified.linkGoogle("g-b", T0)).isFalse();
		assertThat(verified.passwordHash()).contains("hash");
	}

	@Test
	void passwordPolicyIsAboutLengthOnly() {
		assertThat(PasswordPolicy.isAcceptable("una clave larga")).isTrue();
		assertThat(PasswordPolicy.isAcceptable("corta")).isFalse();
		assertThat(PasswordPolicy.isAcceptable("        ")).isFalse();
		assertThat(PasswordPolicy.isAcceptable(null)).isFalse();
		assertThat(PasswordPolicy.isAcceptable("x".repeat(PasswordPolicy.MAX_LENGTH + 1))).isFalse();
	}
}
