package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class PublicIssueReadAdapter implements PublicIssueReadPort {

	private static final String SELECT = "SELECT i.id, i.damage_type, i.status, i.latitude, i.longitude, "
			+ "i.report_count, i.opened_at, i.cover_media_id, " + IssueReadJoins.COLUMNS
			+ " FROM issue i " + IssueReadJoins.JOINS;

	private final JdbcClient jdbc;

	PublicIssueReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PublicIssue> findAllPublic() {
		return jdbc.sql(SELECT + " ORDER BY i.opened_at DESC").query(PublicIssueReadAdapter::map).list();
	}

	@Override
	public Optional<PublicIssue> findPublic(UUID id) {
		return jdbc.sql(SELECT + " WHERE i.id = :id").param("id", id).query(PublicIssueReadAdapter::map).optional();
	}

	private static PublicIssue map(ResultSet rs, int row) throws SQLException {
		return new PublicIssue(
				rs.getObject("id", UUID.class),
				DamageType.valueOf(rs.getString("damage_type")),
				IssueStatus.valueOf(rs.getString("status")),
				rs.getDouble("latitude"),
				rs.getDouble("longitude"),
				rs.getInt("report_count"),
				rs.getTimestamp("opened_at").toInstant(),
				rs.getString("agency_name"),
				rs.getObject("cover_media_id", UUID.class),
				rs.getString("locality_name"),
				IssueReadJoins.channel(rs),
				IssueReadJoins.petition(rs));
	}
}
