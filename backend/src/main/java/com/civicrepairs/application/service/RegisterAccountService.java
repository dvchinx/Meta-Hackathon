package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.RegisterAccountUseCase;
import com.civicrepairs.application.port.out.PasswordHasherPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.PasswordPolicy;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class RegisterAccountService implements RegisterAccountUseCase {

	private final UserAccountRepositoryPort accounts;
	private final PasswordHasherPort hasher;
	private final SessionService sessions;
	private final AccountMailer mailer;
	private final Clock clock;

	RegisterAccountService(UserAccountRepositoryPort accounts, PasswordHasherPort hasher, SessionService sessions,
			AccountMailer mailer, Clock clock) {
		this.accounts = accounts;
		this.hasher = hasher;
		this.sessions = sessions;
		this.mailer = mailer;
		this.clock = clock;
	}

	@Override
	@Transactional
	public String register(String email, String displayName, String password) {
		String normalizedEmail;
		String name;
		try {
			normalizedEmail = UserAccount.normalizeEmail(email);
			name = UserAccount.normalizeDisplayName(displayName);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new AccountException(Code.INVALID_INPUT, "Revisa el correo y el nombre.");
		}
		if (!PasswordPolicy.isAcceptable(password)) {
			throw new AccountException(Code.INVALID_INPUT,
					"La contraseña debe tener entre " + PasswordPolicy.MIN_LENGTH + " y " + PasswordPolicy.MAX_LENGTH
							+ " caracteres.");
		}
		if (accounts.findByEmail(normalizedEmail).isPresent()) {
			throw new AccountException(Code.EMAIL_TAKEN, "Ya existe una cuenta con ese correo.");
		}

		var account = UserAccount.register(UUID.randomUUID(), normalizedEmail, name, hasher.hash(password),
				clock.instant());
		accounts.save(account);
		mailer.sendVerification(account);
		return sessions.open(account.id());
	}
}
