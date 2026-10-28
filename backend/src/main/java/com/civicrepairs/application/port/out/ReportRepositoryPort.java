package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportChannel;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepositoryPort {

	Optional<Report> findById(UUID id);

	/** The reporter's latest report that is still collecting, analyzing or awaiting confirmation. */
	Optional<Report> findOpenByReporter(String reporterId, ReportChannel channel);

	int countCreatedSince(String reporterId, Instant since);

	/** The report that opened the Issue: its confirmed description is the subject of the petition. */
	Optional<Report> findFirstConfirmedByIssue(UUID issueId);

	void save(Report report);
}
