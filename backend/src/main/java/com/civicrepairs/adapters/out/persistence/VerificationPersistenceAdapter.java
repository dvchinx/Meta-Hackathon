package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.VerificationRepositoryPort;
import com.civicrepairs.domain.issue.Verification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
class VerificationPersistenceAdapter implements VerificationRepositoryPort {

	private final JdbcClient jdbc;

	VerificationPersistenceAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Verification v) {
		jdbc.sql("""
						INSERT INTO verification (id, issue_id, verifier_id, photo_media_id, public_media_id, area_before,
						                          area_after, judgement, confidence, reason, outcome, created_at)
						VALUES (:id, :issue, :verifier, :photo, :public, :before, :after, :judgement, :confidence, :reason,
						        :outcome, :at)
						""")
				.param("id", v.id())
				.param("issue", v.issueId())
				.param("verifier", v.verifierId())
				.param("photo", v.photoMediaId())
				.param("public", v.publicMediaId())
				.param("before", v.areaBefore())
				.param("after", v.areaAfter())
				.param("judgement", v.verdict().judgement().name())
				.param("confidence", v.verdict().confidence())
				.param("reason", v.verdict().reason())
				.param("outcome", v.outcome().name())
				.param("at", Timestamp.from(v.at()))
				.update();
	}

	@Override
	public int countByVerifierSince(String verifierId, Instant since) {
		return jdbc.sql("SELECT count(*) FROM verification WHERE verifier_id = :verifier AND created_at >= :since")
				.param("verifier", verifierId)
				.param("since", Timestamp.from(since))
				.query(Integer.class)
				.single();
	}
}
