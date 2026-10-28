package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.EndorseIssueUseCase;
import com.civicrepairs.application.port.out.IssueEndorsementPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.domain.issue.Issue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class EndorseIssueService implements EndorseIssueUseCase {

	private final IssueRepositoryPort issues;
	private final IssueEndorsementPort endorsements;
	private final ReporterIds reporterIds;

	EndorseIssueService(IssueRepositoryPort issues, IssueEndorsementPort endorsements, ReporterIds reporterIds) {
		this.issues = issues;
		this.endorsements = endorsements;
		this.reporterIds = reporterIds;
	}

	@Override
	@Transactional
	public EndorsementResult endorse(UUID issueId, UUID userId) {
		Issue issue = issues.findByIdForUpdate(issueId)
				.orElseThrow(() -> new IllegalArgumentException("unknown issue " + issueId));
		if (issue.status().isClosed() || !endorsements.add(issueId, reporterIds.forUser(userId))) {
			return new EndorsementResult(issueId, false, issue.reportCount());
		}
		issue.addReport();
		issues.save(issue);
		return new EndorsementResult(issueId, true, issue.reportCount());
	}
}
