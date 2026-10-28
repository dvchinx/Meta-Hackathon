package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.GoogleSignInUseCase;
import com.civicrepairs.application.port.out.GoogleIdentityPort;
import com.civicrepairs.application.port.out.GoogleIdentityPort.GoogleIdentity;
import com.civicrepairs.application.port.out.SessionRepositoryPort;
import com.civicrepairs.application.port.out.UserAccountRepositoryPort;
import com.civicrepairs.domain.account.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds the account by Google id, then by email (linking it), and otherwise creates one. Only addresses Google
 * reports as verified are accepted, so linking by email is safe.
 */
@Service
class GoogleSignInService implements GoogleSignInUseCase {

	private static final String FALLBACK_NAME = "Ciudadano";

	private final GoogleIdentityPort google;
	private final UserAccountRepositoryPort accounts;
	private final SessionRepositoryPort sessionStore;
	private final SessionService sessions;
	private final Clock clock;

	GoogleSignInService(GoogleIdentityPort google, UserAccountRepositoryPort accounts,
			SessionRepositoryPort sessionStore, SessionService sessions, Clock clock) {
		this.google = google;
		this.accounts = accounts;
		this.sessionStore = sessionStore;
		this.sessions = sessions;
		this.clock = clock;
	}

	@Override
	@Transactional
	public String signIn(String idToken) {
		GoogleIdentity identity = (idToken == null ? Optional.<GoogleIdentity>empty() : google.verify(idToken))
				.filter(GoogleIdentity::emailVerified)
				.orElseThrow(() -> new AccountException(Code.INVALID_TOKEN,
						"No pudimos verificar tu cuenta de Google. Intenta de nuevo."));

		UserAccount account = accounts.findByGoogleSubject(identity.subject()).orElse(null);
		if (account == null) {
			account = accounts.findByEmail(UserAccount.normalizeEmail(identity.email())).orElse(null);
			if (account != null && account.linkGoogle(identity.subject(), clock.instant())) {
				sessionStore.deleteAllForUser(account.id());
			}
		}
		if (account == null) {
			account = UserAccount.registerWithGoogle(UUID.randomUUID(), identity.email(), displayName(identity),
					identity.subject(), clock.instant());
		}
		accounts.save(account);
		return sessions.open(account.id());
	}

	private static String displayName(GoogleIdentity identity) {
		String candidate = identity.name() != null && !identity.name().isBlank() ? identity.name()
				: identity.email().substring(0, identity.email().indexOf('@'));
		try {
			return UserAccount.normalizeDisplayName(candidate.length() > UserAccount.MAX_DISPLAY_NAME
					? candidate.substring(0, UserAccount.MAX_DISPLAY_NAME) : candidate);
		} catch (IllegalArgumentException e) {
			return FALLBACK_NAME;
		}
	}
}
