package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.GeoQueryPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.StatusTransition;
import com.civicrepairs.domain.issue.TransitionCause;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import com.civicrepairs.domain.shared.Locality;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class IssuePersistenceAdapter implements IssueRepositoryPort, GeoQueryPort {

	private final IssueJpaRepository issues;
	private final StatusTransitionJpaRepository transitions;
	private final JdbcClient jdbc;

	IssuePersistenceAdapter(IssueJpaRepository issues, StatusTransitionJpaRepository transitions, JdbcClient jdbc) {
		this.issues = issues;
		this.transitions = transitions;
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Issue> findById(UUID id) {
		return issues.findById(id).map(IssuePersistenceAdapter::toDomain);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Issue> findByIdForUpdate(UUID id) {
		return issues.findByIdForUpdate(id).map(IssuePersistenceAdapter::toDomain);
	}

	@Override
	@Transactional
	public void save(Issue issue) {
		IssueJpaEntity entity = issues.findById(issue.id()).orElseGet(() -> newEntity(issue));
		entity.status = issue.status().name();
		entity.reportCount = issue.reportCount();

		IssueJpaEntity saved = issues.save(entity);

		List<StatusTransition> all = issue.transitions();
		for (int seq = saved.transitions.size(); seq < all.size(); seq++) {
			StatusTransition t = all.get(seq);
			StatusTransitionJpaEntity transition = transitions.save(toEntity(saved, seq, t));
			saved.transitions.add(transition);
			if (t.from() != null) {
				// Same transaction as the change: a notice is never lost, and never sent for a rolled-back change.
				jdbc.sql("INSERT INTO notification_outbox (issue_id, to_status, created_at) VALUES (:issue, :status, :at)")
						.param("issue", issue.id())
						.param("status", t.to().name())
						.param("at", Timestamp.from(t.at()))
						.update();
			}
		}
	}

	@Override
	public List<UUID> findOpenIssuesNear(GeoPoint point, double meters, DamageType damageType, int limit) {
		return jdbc.sql("""
						SELECT id FROM issue
						WHERE damage_type = :type
						  AND status <> 'VERIFIED'
						  AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :meters)
						ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
						LIMIT :limit
						""")
				.param("type", damageType.name())
				.param("lon", point.longitude())
				.param("lat", point.latitude())
				.param("meters", meters)
				.param("limit", limit)
				.query(UUID.class)
				.list();
	}

	@Override
	public Optional<Locality> findLocality(GeoPoint point) {
		return jdbc.sql("""
						SELECT code, name FROM locality
						WHERE ST_Covers(boundary, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
						ORDER BY code
						LIMIT 1
						""")
				.param("lon", point.longitude())
				.param("lat", point.latitude())
				.query((rs, n) -> new Locality(rs.getString("code"), rs.getString("name")))
				.optional();
	}

	private static IssueJpaEntity newEntity(Issue issue) {
		var entity = new IssueJpaEntity();
		entity.id = issue.id();
		entity.damageType = issue.damageType().name();
		entity.latitude = issue.location().latitude();
		entity.longitude = issue.location().longitude();
		entity.openedAt = issue.openedAt();
		entity.agencyCode = issue.agencyCode().orElse(null);
		entity.coverMediaId = issue.coverMediaId().orElse(null);
		return entity;
	}

	private static StatusTransitionJpaEntity toEntity(IssueJpaEntity issue, int seq, StatusTransition t) {
		var entity = new StatusTransitionJpaEntity();
		entity.issue = issue;
		entity.seq = seq;
		entity.fromStatus = t.from() == null ? null : t.from().name();
		entity.toStatus = t.to().name();
		entity.cause = t.cause().name();
		entity.evidenceRef = t.evidenceRef();
		entity.at = t.at();
		return entity;
	}

	private static Issue toDomain(IssueJpaEntity e) {
		List<StatusTransition> transitions = e.transitions.stream()
				.map(t -> new StatusTransition(
						t.fromStatus == null ? null : IssueStatus.valueOf(t.fromStatus),
						IssueStatus.valueOf(t.toStatus),
						TransitionCause.valueOf(t.cause),
						t.evidenceRef,
						t.at))
				.toList();
		return Issue.rehydrate(e.id, DamageType.valueOf(e.damageType), new GeoPoint(e.latitude, e.longitude),
				e.openedAt, e.agencyCode, e.coverMediaId, IssueStatus.valueOf(e.status), e.reportCount, transitions);
	}
}
