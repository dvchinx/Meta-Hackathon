package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.EmailVerificationUseCase;
import com.civicrepairs.application.port.out.EmailTokenRepositoryPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.EmailTokenPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class EmailVerificationService implements EmailVerificationUseCase {

	private final UserAccountRepositoryPort accounts;
	private final EmailTokenRepositoryPort tokens;
	private final AccountMailer mailer;
	private final Clock clock;

	EmailVerificationService(UserAccountRepositoryPort accounts, EmailTokenRepositoryPort tokens, AccountMailer mailer,
			Clock clock) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.mailer = mailer;
		this.clock = clock;
	}

	@Override
	@Transactional
	public AuthenticatedUser verify(String token) {
		if (!Secrets.looksValid(token)) {
			throw invalid();
		}
		UUID userId = tokens.consume(Secrets.hash(token), EmailTokenPurpose.VERIFY_EMAIL, clock.instant())
				.orElseThrow(EmailVerificationService::invalid);
		var account = accounts.findById(userId).orElseThrow(EmailVerificationService::invalid);
		account.verifyEmail(clock.instant());
		accounts.save(account);
		return SessionService.toAuthenticated(account);
	}

	@Override
	@Transactional
	public void resend(UUID userId) {
		accounts.findById(userId).filter(a -> !a.isEmailVerified()).ifPresent(mailer::sendVerification);
	}

	private static AccountException invalid() {
		return new AccountException(Code.INVALID_TOKEN, "El enlace no es válido o ya venció. Pide uno nuevo.");
	}
}
