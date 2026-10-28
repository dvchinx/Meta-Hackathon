package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.issue.Verification;

import java.time.Instant;

public interface VerificationRepositoryPort {

	void save(Verification verification);

	int countByVerifierSince(String verifierId, Instant since);
}
