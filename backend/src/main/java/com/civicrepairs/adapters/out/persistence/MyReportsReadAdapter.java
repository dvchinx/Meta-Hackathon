package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.MyReport;
import com.civicrepairs.application.port.out.MyReportsReadPort;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class MyReportsReadAdapter implements MyReportsReadPort {

	private final JdbcClient jdbc;

	MyReportsReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<MyReport> findByReporter(String reporterId, UUID userId) {
		return jdbc.sql("""
						SELECT r.id, r.status, r.created_at, r.latitude, r.longitude,
						       r.interpretation ->> 'damageType' AS damage_type,
						       r.interpretation ->> 'summary'    AS summary,
						       (r.analysis ->> 'publicMediaId')::uuid AS cover_media_id,
						       i.id AS issue_id, i.status AS issue_status, i.report_count,
						       (SELECT p.id FROM petition p WHERE p.issue_id = i.id AND p.filer_user_id = :user
						        ORDER BY p.filed_at DESC LIMIT 1) AS my_petition_id,
						""" + IssueReadJoins.COLUMNS + """
						FROM report r LEFT JOIN issue i ON i.id = r.issue_id
						""" + IssueReadJoins.JOINS + """
						WHERE r.reporter_id = :reporter AND r.status <> 'CANCELLED' AND r.latitude IS NOT NULL
						ORDER BY r.created_at DESC
						""")
				.param("reporter", reporterId)
				.param("user", userId)
				.query((rs, n) -> {
					String damageType = rs.getString("damage_type");
					String issueStatus = rs.getString("issue_status");
					return new MyReport(
							rs.getObject("id", UUID.class),
							ReportStatus.valueOf(rs.getString("status")),
							rs.getTimestamp("created_at").toInstant(),
							damageType == null ? null : DamageType.valueOf(damageType),
							rs.getString("summary"),
							rs.getDouble("latitude"),
							rs.getDouble("longitude"),
							rs.getObject("issue_id", UUID.class),
							issueStatus == null ? null : IssueStatus.valueOf(issueStatus),
							rs.getInt("report_count"),
							rs.getObject("cover_media_id", UUID.class),
							rs.getString("agency_name"),
							rs.getString("locality_name"),
							IssueReadJoins.petition(rs),
							rs.getObject("my_petition_id", UUID.class));
				})
				.list();
	}
}
