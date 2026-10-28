package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.in.AdminOverview.ReviewItem;
import com.civicrepairs.application.port.out.AdminReadPort;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.shared.DamageType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class AdminReadAdapter implements AdminReadPort {

	private final JdbcClient jdbc;

	AdminReadAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<IssueStatus, Integer> issuesByStatus() {
		Map<IssueStatus, Integer> counts = new EnumMap<>(IssueStatus.class);
		for (IssueStatus status : IssueStatus.values()) {
			counts.put(status, 0);
		}
		jdbc.sql("SELECT status, count(*) AS n FROM issue GROUP BY status")
				.query(rs -> {
					counts.put(IssueStatus.valueOf(rs.getString("status")), rs.getInt("n"));
				});
		return counts;
	}

	@Override
	public List<ReviewItem> manualReview(int limit) {
		return jdbc.sql("""
						SELECT id, channel, created_at, review_reason,
						       interpretation ->> 'damageType' AS damage_type,
						       interpretation ->> 'summary'    AS summary
						FROM report WHERE status = 'MANUAL_REVIEW'
						ORDER BY created_at DESC
						LIMIT :n
						""")
				.param("n", limit)
				.query((rs, n) -> {
					String type = rs.getString("damage_type");
					return new ReviewItem(rs.getObject("id", UUID.class), ReportChannel.valueOf(rs.getString("channel")),
							rs.getTimestamp("created_at").toInstant(), type == null ? null : DamageType.valueOf(type),
							rs.getString("summary"), rs.getString("review_reason"));
				})
				.list();
	}
}
