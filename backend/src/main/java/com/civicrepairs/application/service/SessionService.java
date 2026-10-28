package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.SessionUseCase;
import com.civicrepairs.application.port.out.PasswordHasherPort;
import com.civicrepairs.application.port.out.SessionRepositoryPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
class SessionService implements SessionUseCase {

	static final Duration SESSION_TTL = Duration.ofDays(30);
	/** Hashed once at startup so a login attempt for an unknown email takes as long as a wrong password. */
	private final String decoyHash;

	private final UserAccountRepositoryPort accounts;
	private final SessionRepositoryPort sessions;
	private final PasswordHasherPort hasher;
	private final Clock clock;

	SessionService(UserAccountRepositoryPort accounts, SessionRepositoryPort sessions, PasswordHasherPort hasher,
			Clock clock) {
		this.accounts = accounts;
		this.sessions = sessions;
		this.hasher = hasher;
		this.clock = clock;
		this.decoyHash = hasher.hash(Secrets.generate());
	}

	@Override
	@Transactional
	public String login(String email, String password) {
		Optional<UserAccount> account;
		try {
			account = accounts.findByEmail(UserAccount.normalizeEmail(email));
		} catch (IllegalArgumentException | NullPointerException e) {
			account = Optional.empty();
		}
		String hash = account.flatMap(UserAccount::passwordHash).orElse(decoyHash);
		boolean ok = password != null && hasher.matches(password, hash) && account.isPresent();
		if (!ok) {
			throw new AccountException(Code.BAD_CREDENTIALS, "Correo o contraseña incorrectos.");
		}
		return open(account.get().id());
	}

	@Override
	@Transactional
	public void logout(String sessionSecret) {
		if (Secrets.looksValid(sessionSecret)) {
			sessions.delete(Secrets.hash(sessionSecret));
		}
	}

	@Override
	@Transactional
	public Optional<AuthenticatedUser> resolve(String sessionSecret) {
		if (!Secrets.looksValid(sessionSecret)) {
			return Optional.empty();
		}
		return sessions.findUser(Secrets.hash(sessionSecret), clock.instant())
				.flatMap(accounts::findById)
				.map(SessionService::toAuthenticated);
	}

	String open(UUID userId) {
		String secret = Secrets.generate();
		sessions.create(Secrets.hash(secret), userId, clock.instant().plus(SESSION_TTL));
		return secret;
	}

	static AuthenticatedUser toAuthenticated(UserAccount account) {
		return new AuthenticatedUser(account.id(), account.email(), account.displayName(), account.isEmailVerified());
	}
}
