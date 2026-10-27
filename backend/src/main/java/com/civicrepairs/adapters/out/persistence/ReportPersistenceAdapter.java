package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.report.DamageAnalysis;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.GeoPoint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class ReportPersistenceAdapter implements ReportRepositoryPort {

	private static final String COLUMNS = """
			id, channel, reporter_id, status, photo_media_id, context, latitude, longitude,
			interpretation::text AS interpretation, analysis::text AS analysis, issue_id, review_reason, created_at
			""";

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final Clock clock;

	ReportPersistenceAdapter(JdbcClient jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Override
	public Optional<Report> findById(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM report WHERE id = :id")
				.param("id", id)
				.query(this::map)
				.optional();
	}

	@Override
	public Optional<Report> findOpenByReporter(String reporterId, ReportChannel channel) {
		return jdbc.sql("SELECT " + COLUMNS + """
						 FROM report
						WHERE reporter_id = :reporter AND channel = :channel
						  AND status IN ('COLLECTING', 'ANALYZING', 'AWAITING_CONFIRMATION')
						ORDER BY created_at DESC
						LIMIT 1
						""")
				.param("reporter", reporterId)
				.param("channel", channel.name())
				.query(this::map)
				.optional();
	}

	@Override
	public int countCreatedSince(String reporterId, Instant since) {
		return jdbc.sql("SELECT count(*) FROM report WHERE reporter_id = :reporter AND created_at >= :since")
				.param("reporter", reporterId)
				.param("since", Timestamp.from(since))
				.query(Integer.class)
				.single();
	}

	@Override
	public void save(Report r) {
		jdbc.sql("""
						INSERT INTO report (id, channel, reporter_id, status, photo_media_id, context, latitude, longitude,
						                    interpretation, analysis, issue_id, review_reason, created_at, updated_at)
						VALUES (:id, :channel, :reporter, :status, :photo, :context, :lat, :lon,
						        CAST(:interpretation AS jsonb), CAST(:analysis AS jsonb), :issue, :reason, :created, :now)
						ON CONFLICT (id) DO UPDATE SET
						    status = EXCLUDED.status, photo_media_id = EXCLUDED.photo_media_id, context = EXCLUDED.context,
						    latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude,
						    interpretation = EXCLUDED.interpretation, analysis = EXCLUDED.analysis,
						    issue_id = EXCLUDED.issue_id, review_reason = EXCLUDED.review_reason, updated_at = EXCLUDED.updated_at
						""")
				.param("id", r.id())
				.param("channel", r.channel().name())
				.param("reporter", r.reporterId())
				.param("status", r.status().name())
				.param("photo", r.photoMediaId().orElse(null))
				.param("context", r.context().orElse(null))
				.param("lat", r.location().map(GeoPoint::latitude).orElse(null))
				.param("lon", r.location().map(GeoPoint::longitude).orElse(null))
				.param("interpretation", r.interpretation().map(json::writeValueAsString).orElse(null))
				.param("analysis", r.analysis().map(json::writeValueAsString).orElse(null))
				.param("issue", r.issueId().orElse(null))
				.param("reason", r.reviewReason().orElse(null))
				.param("created", Timestamp.from(r.createdAt()))
				.param("now", Timestamp.from(clock.instant()))
				.update();
	}

	private Report map(ResultSet rs, int row) throws SQLException {
		double lat = rs.getDouble("latitude");
		GeoPoint location = rs.wasNull() ? null : new GeoPoint(lat, rs.getDouble("longitude"));
		String interpretation = rs.getString("interpretation");
		String analysis = rs.getString("analysis");
		return Report.rehydrate(
				rs.getObject("id", UUID.class),
				ReportChannel.valueOf(rs.getString("channel")),
				rs.getString("reporter_id"),
				rs.getTimestamp("created_at").toInstant(),
				ReportStatus.valueOf(rs.getString("status")),
				rs.getObject("photo_media_id", UUID.class),
				rs.getString("context"),
				location,
				interpretation == null ? null : json.readValue(interpretation, Interpretation.class),
				analysis == null ? null : json.readValue(analysis, DamageAnalysis.class),
				rs.getObject("issue_id", UUID.class),
				rs.getString("review_reason"));
	}
}
