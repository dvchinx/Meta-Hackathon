package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.AgencyResponseRepositoryPort;
import com.civicrepairs.domain.issue.AgencyResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
class AgencyResponsePersistenceAdapter implements AgencyResponseRepositoryPort {

	private final JdbcClient jdbc;

	AgencyResponsePersistenceAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(AgencyResponse r) {
		jdbc.sql("""
						INSERT INTO agency_response (id, issue_id, body, classification, summary, received_at)
						VALUES (:id, :issue, :body, :classification, :summary, :at)
						""")
				.param("id", r.id())
				.param("issue", r.issueId())
				.param("body", r.body())
				.param("classification", r.classification().name())
				.param("summary", r.summary())
				.param("at", Timestamp.from(r.receivedAt()))
				.update();
	}
}
