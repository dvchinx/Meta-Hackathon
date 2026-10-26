package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class PublicIssueReadAdapter implements PublicIssueReadPort {

	private final JdbcClient jdbc;

	PublicIssueReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PublicIssue> findAllPublic() {
		return jdbc.sql("""
						SELECT i.id, i.damage_type, i.status, i.latitude, i.longitude, i.report_count, i.opened_at,
						       a.name AS agency_name, i.cover_media_id
						FROM issue i LEFT JOIN agency a ON a.code = i.agency_code
						ORDER BY i.opened_at DESC
						""")
				.query((rs, n) -> new PublicIssue(
						rs.getObject("id", UUID.class),
						DamageType.valueOf(rs.getString("damage_type")),
						IssueStatus.valueOf(rs.getString("status")),
						rs.getDouble("latitude"),
						rs.getDouble("longitude"),
						rs.getInt("report_count"),
						rs.getTimestamp("opened_at").toInstant(),
						rs.getString("agency_name"),
						rs.getObject("cover_media_id", UUID.class)))
				.list();
	}
}
