package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.issue.Issue;

import java.util.Optional;
import java.util.UUID;

public interface IssueRepositoryPort {

	Optional<Issue> findById(UUID id);

	/** Loads the Issue and locks it until the current transaction ends (concurrent reports of the same damage). */
	Optional<Issue> findByIdForUpdate(UUID id);

	/** Inserts or updates the Issue and appends its new status transitions. Existing transitions are never touched. */
	void save(Issue issue);
}
