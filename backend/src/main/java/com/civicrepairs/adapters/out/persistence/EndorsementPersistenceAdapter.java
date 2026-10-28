package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.IssueEndorsementPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Component
class EndorsementPersistenceAdapter implements IssueEndorsementPort {

	private final JdbcClient jdbc;
	private final Clock clock;

	EndorsementPersistenceAdapter(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public boolean add(UUID issueId, String reporterId) {
		// Someone who already reported this damage is already counted in report_count.
		return jdbc.sql("""
						INSERT INTO issue_endorsement (issue_id, reporter_id, created_at)
						SELECT :issue, :reporter, :now
						WHERE NOT EXISTS (
						    SELECT 1 FROM report WHERE issue_id = :issue AND reporter_id = :reporter AND status = 'CONFIRMED')
						ON CONFLICT DO NOTHING
						""")
				.param("issue", issueId)
				.param("reporter", reporterId)
				.param("now", Timestamp.from(clock.instant()))
				.update() == 1;
	}
}
