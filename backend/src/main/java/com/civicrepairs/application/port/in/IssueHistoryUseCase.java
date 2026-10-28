package com.civicrepairs.application.port.in;

import java.util.UUID;

public interface IssueHistoryUseCase {

	IssueHistory history(UUID issueId);
}
