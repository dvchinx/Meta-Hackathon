package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.AgencyScorecard;
import com.civicrepairs.application.port.out.AgencyScorecardReadPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One pass over the Issues of each agency. A repair claim is a transition to REPAIR_CLAIMED; a refuted claim, a
 * transition to REOPENED (only a before/after photo causes one). Overdue: the latest petition is past its due date
 * and the agency has not replied since it was filed.
 */
@Component
class AgencyScorecardReadAdapter implements AgencyScorecardReadPort {

	private static final String SQL = """
			WITH per_issue AS (
			    SELECT i.agency_code, i.status, i.opened_at, i.seed_batch IS NOT NULL AS example,
			           pet.filed_at, pet.due_date,
			           EXISTS (SELECT 1 FROM agency_response r
			                   WHERE r.issue_id = i.id AND r.received_at >= pet.filed_at) AS answered,
			           EXISTS (SELECT 1 FROM status_transition t
			                   WHERE t.issue_id = i.id AND t.to_status = 'REPAIR_CLAIMED') AS claimed,
			           EXISTS (SELECT 1 FROM status_transition t
			                   WHERE t.issue_id = i.id AND t.to_status = 'REOPENED') AS refuted
			    FROM issue i
			    LEFT JOIN LATERAL (
			        SELECT filed_at, due_date FROM petition WHERE issue_id = i.id ORDER BY filed_at DESC LIMIT 1
			    ) pet ON true
			    WHERE i.agency_code IS NOT NULL
			), responses AS (
			    SELECT i.agency_code,
			           count(*) FILTER (WHERE r.classification = 'RESOLVES') AS resolving,
			           count(*) FILTER (WHERE r.classification <> 'RESOLVES') AS evasive
			    FROM agency_response r JOIN issue i ON i.id = r.issue_id
			    GROUP BY i.agency_code
			)
			SELECT a.code, a.name,
			       count(*) AS issues,
			       count(*) FILTER (WHERE p.status <> 'VERIFIED') AS open,
			       count(*) FILTER (WHERE p.status = 'VERIFIED') AS verified,
			       avg(extract(epoch FROM (:now - p.opened_at)) / 86400) FILTER (WHERE p.status <> 'VERIFIED')
			           AS average_days_open,
			       count(p.filed_at) AS filed,
			       count(*) FILTER (WHERE p.due_date < :today AND NOT p.answered AND p.status <> 'VERIFIED')
			           AS overdue,
			       coalesce(max(r.resolving), 0) AS resolving,
			       coalesce(max(r.evasive), 0) AS evasive,
			       count(*) FILTER (WHERE p.claimed) AS claims,
			       count(*) FILTER (WHERE p.claimed AND p.status = 'VERIFIED') AS confirmed,
			       count(*) FILTER (WHERE p.refuted) AS refuted,
			       count(*) FILTER (WHERE p.example) AS examples
			FROM per_issue p
			JOIN agency a ON a.code = p.agency_code
			LEFT JOIN responses r ON r.agency_code = p.agency_code
			GROUP BY a.code, a.name
			ORDER BY overdue DESC, open DESC, a.name
			""";

	private final JdbcClient jdbc;

	AgencyScorecardReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<AgencyScorecard> scorecards(Instant now, LocalDate today) {
		return jdbc.sql(SQL)
				.param("now", Timestamp.from(now))
				.param("today", Date.valueOf(today))
				.query(AgencyScorecardReadAdapter::map)
				.list();
	}

	private static AgencyScorecard map(ResultSet rs, int row) throws SQLException {
		double average = rs.getDouble("average_days_open");
		Double averageDaysOpen = rs.wasNull() ? null : average;
		return new AgencyScorecard(
				rs.getString("code"),
				rs.getString("name"),
				rs.getInt("issues"),
				rs.getInt("open"),
				rs.getInt("verified"),
				averageDaysOpen,
				rs.getInt("filed"),
				rs.getInt("overdue"),
				rs.getInt("resolving"),
				rs.getInt("evasive"),
				rs.getInt("claims"),
				rs.getInt("confirmed"),
				rs.getInt("refuted"),
				rs.getInt("examples"));
	}
}
