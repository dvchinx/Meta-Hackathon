package com.civicrepairs.support;

import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory fake of the report repository port. */
public class InMemoryReportRepository implements ReportRepositoryPort {

	private final Map<UUID, Report> reports = new LinkedHashMap<>();

	@Override
	public Optional<Report> findById(UUID id) {
		return Optional.ofNullable(reports.get(id));
	}

	@Override
	public Optional<Report> findOpenByReporter(String reporterId, ReportChannel channel) {
		return reports.values().stream()
				.filter(r -> r.reporterId().equals(reporterId) && r.channel() == channel && r.status().isOpen())
				.max(Comparator.comparing(Report::createdAt));
	}

	@Override
	public int countCreatedSince(String reporterId, Instant since) {
		return (int) reports.values().stream()
				.filter(r -> r.reporterId().equals(reporterId) && !r.createdAt().isBefore(since))
				.count();
	}

	@Override
	public Optional<Report> findFirstConfirmedByIssue(UUID issueId) {
		return reports.values().stream()
				.filter(r -> r.status() == ReportStatus.CONFIRMED && r.issueId().equals(Optional.of(issueId)))
				.min(Comparator.comparing(Report::createdAt));
	}

	@Override
	public void save(Report report) {
		reports.put(report.id(), report);
	}
}
