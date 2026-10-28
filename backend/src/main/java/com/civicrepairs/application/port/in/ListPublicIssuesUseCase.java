package com.civicrepairs.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListPublicIssuesUseCase {

	List<PublicIssue> listPublicIssues();

	Optional<PublicIssue> findPublicIssue(UUID id);
}
