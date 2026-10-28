package com.civicrepairs.adapters.out.security;

import com.civicrepairs.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt at cost 11: ~150 ms per check, enough to make guessing expensive without slowing sign-in. */
@Component
class BCryptPasswordHasher implements PasswordHasherPort {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String hash) {
		return hash != null && encoder.matches(rawPassword, hash);
	}
}
