package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ConfirmReportUseCase;
import com.civicrepairs.application.port.in.ReportNotFoundException;
import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.application.port.out.GeoQueryPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.report.DamageAnalysis;
import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deduplication: an open Issue of the same type within the radius whose photo Muse Spark judges to be the same
 * damage. The visual check runs before taking any lock; the Issue is then re-read under lock.
 */
@Service
class ConfirmReportService implements ConfirmReportUseCase {

	private static final Logger log = LoggerFactory.getLogger(ConfirmReportService.class);
	private static final int MAX_CANDIDATES = 3;

	private final ReportRepositoryPort reports;
	private final IssueRepositoryPort issues;
	private final GeoQueryPort geo;
	private final AgencyRepositoryPort agencies;
	private final MediaStoragePort media;
	private final LanguageModelPort languageModel;
	private final ReportSettings settings;
	private final TransactionTemplate tx;
	private final Clock clock;

	ConfirmReportService(ReportRepositoryPort reports, IssueRepositoryPort issues, GeoQueryPort geo,
			AgencyRepositoryPort agencies, MediaStoragePort media, LanguageModelPort languageModel,
			ReportSettings settings, TransactionTemplate tx, Clock clock) {
		this.reports = reports;
		this.issues = issues;
		this.geo = geo;
		this.agencies = agencies;
		this.media = media;
		this.languageModel = languageModel;
		this.settings = settings;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public ConfirmationResult confirm(UUID reportId, String reporterId, boolean accepted) {
		Report report = reports.findById(reportId)
				.filter(r -> r.reporterId().equals(reporterId))
				.orElseThrow(() -> new ReportNotFoundException(reportId));
		if (!accepted) {
			return tx.execute(status -> {
				report.cancel();
				reports.save(report);
				return new ConfirmationResult(reportId, false, null, false, 0);
			});
		}
		if (report.status() != ReportStatus.AWAITING_CONFIRMATION) {
			throw new IllegalStateException("Report " + reportId + " is " + report.status());
		}

		DamageType type = report.interpretation().orElseThrow().damageType();
		GeoPoint location = report.location().orElseThrow();
		DamageAnalysis analysis = report.analysis().orElseThrow();
		Optional<UUID> duplicateOf = findDuplicate(type, location, analysis.publicMediaId());

		return tx.execute(status -> {
			Issue issue = duplicateOf.flatMap(issues::findByIdForUpdate)
					.filter(existing -> !existing.status().isClosed())
					.orElse(null);
			boolean newIssue = issue == null;
			if (newIssue) {
				String agency = agencies.findResponsibleFor(type).map(Agency::code).orElse(null);
				issue = Issue.open(UUID.randomUUID(), type, location, agency, analysis.publicMediaId(),
						reportId.toString(), clock.instant());
			} else {
				issue.addReport();
			}
			issues.save(issue);
			report.confirm(issue.id());
			reports.save(report);
			return new ConfirmationResult(reportId, true, issue.id(), newIssue, issue.reportCount());
		});
	}

	private Optional<UUID> findDuplicate(DamageType type, GeoPoint location, UUID candidatePhoto) {
		List<UUID> nearby = geo.findOpenIssuesNear(location, settings.dedupRadiusMeters(), type, MAX_CANDIDATES);
		if (nearby.isEmpty()) {
			return Optional.empty();
		}
		var candidate = media.load(candidatePhoto);
		for (UUID issueId : nearby) {
			Optional<UUID> cover = issues.findById(issueId).flatMap(Issue::coverMediaId);
			if (cover.isEmpty()) {
				continue;
			}
			try {
				var verdict = languageModel.sameDamage(media.load(cover.get()), candidate);
				if (verdict.sameDamage() && verdict.confidence() >= settings.sameDamageMinConfidence()) {
					return Optional.of(issueId);
				}
			} catch (ModelUnavailableException e) {
				// Without the visual check, same type within the radius is still a strong signal.
				log.warn("Same-damage check unavailable ({}); joining nearest Issue {}", e.reason(), nearby.getFirst());
				return Optional.of(nearby.getFirst());
			}
		}
		return Optional.empty();
	}
}
