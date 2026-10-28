package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.PasswordResetUseCase;
import com.civicrepairs.application.port.out.EmailTokenRepositoryPort;
import com.civicrepairs.application.port.out.PasswordHasherPort;
import com.civicrepairs.application.port.out.SessionRepositoryPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.EmailTokenPurpose;
import com.civicrepairs.domain.account.PasswordPolicy;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class PasswordResetService implements PasswordResetUseCase {

	private final UserAccountRepositoryPort accounts;
	private final EmailTokenRepositoryPort tokens;
	private final SessionRepositoryPort sessions;
	private final PasswordHasherPort hasher;
	private final AccountMailer mailer;
	private final Clock clock;

	PasswordResetService(UserAccountRepositoryPort accounts, EmailTokenRepositoryPort tokens,
			SessionRepositoryPort sessions, PasswordHasherPort hasher, AccountMailer mailer, Clock clock) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.sessions = sessions;
		this.hasher = hasher;
		this.mailer = mailer;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void request(String email) {
		try {
			accounts.findByEmail(UserAccount.normalizeEmail(email)).ifPresent(mailer::sendPasswordReset);
		} catch (IllegalArgumentException | NullPointerException e) {
			// Unknown or malformed address: same silence as an unknown account.
		}
	}

	@Override
	@Transactional
	public void reset(String token, String newPassword) {
		if (!PasswordPolicy.isAcceptable(newPassword)) {
			throw new AccountException(Code.INVALID_INPUT,
					"La contraseña debe tener entre " + PasswordPolicy.MIN_LENGTH + " y " + PasswordPolicy.MAX_LENGTH
							+ " caracteres.");
		}
		if (!Secrets.looksValid(token)) {
			throw invalid();
		}
		UUID userId = tokens.consume(Secrets.hash(token), EmailTokenPurpose.RESET_PASSWORD, clock.instant())
				.orElseThrow(PasswordResetService::invalid);
		var account = accounts.findById(userId).orElseThrow(PasswordResetService::invalid);
		account.changePassword(hasher.hash(newPassword));
		// Proving control of the mailbox is as good as the verification link.
		account.verifyEmail(clock.instant());
		accounts.save(account);
		sessions.deleteAllForUser(userId);
	}

	private static AccountException invalid() {
		return new AccountException(Code.INVALID_TOKEN, "El enlace no es válido o ya venció. Pide uno nuevo.");
	}
}
