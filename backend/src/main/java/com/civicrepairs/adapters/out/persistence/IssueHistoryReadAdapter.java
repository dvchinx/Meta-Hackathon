package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.IssueHistory;
import com.civicrepairs.application.port.out.IssueHistoryReadPort;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.TransitionCause;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Public timeline. Selects only what the public may see: never reporter, petitioner or verifier ids. */
@Component
class IssueHistoryReadAdapter implements IssueHistoryReadPort {

	private final JdbcClient jdbc;

	IssueHistoryReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public IssueHistory history(UUID issueId) {
		var steps = jdbc.sql("SELECT from_status, to_status, cause, at FROM status_transition WHERE issue_id = :id ORDER BY seq")
				.param("id", issueId)
				.query((rs, n) -> {
					String from = rs.getString("from_status");
					return new IssueHistory.Step(from == null ? null : IssueStatus.valueOf(from),
							IssueStatus.valueOf(rs.getString("to_status")), TransitionCause.valueOf(rs.getString("cause")),
							rs.getTimestamp("at").toInstant());
				})
				.list();
		var responses = jdbc.sql("""
						SELECT received_at, classification, summary, body FROM agency_response
						WHERE issue_id = :id ORDER BY received_at
						""")
				.param("id", issueId)
				.query((rs, n) -> new IssueHistory.Response(rs.getTimestamp("received_at").toInstant(),
						AgencyResponseClassification.valueOf(rs.getString("classification")), rs.getString("summary"),
						rs.getString("body")))
				.list();
		var checks = jdbc.sql("""
						SELECT created_at, outcome, reason, public_media_id FROM verification
						WHERE issue_id = :id ORDER BY created_at
						""")
				.param("id", issueId)
				.query((rs, n) -> new IssueHistory.Check(rs.getTimestamp("created_at").toInstant(),
						Outcome.valueOf(rs.getString("outcome")), rs.getString("reason"),
						rs.getObject("public_media_id", UUID.class)))
				.list();
		return new IssueHistory(steps, responses, checks);
	}
}
