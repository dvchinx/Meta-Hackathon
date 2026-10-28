package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.account.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepositoryPort {

	Optional<UserAccount> findById(UUID id);

	/** @param email already normalized (see {@link UserAccount#normalizeEmail}) */
	Optional<UserAccount> findByEmail(String email);

	Optional<UserAccount> findByGoogleSubject(String subject);

	void save(UserAccount account);
}
