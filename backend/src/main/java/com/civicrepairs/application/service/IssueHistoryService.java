package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.IssueHistory;
import com.civicrepairs.application.port.in.IssueHistoryUseCase;
import com.civicrepairs.application.port.out.IssueHistoryReadPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class IssueHistoryService implements IssueHistoryUseCase {

	private final IssueHistoryReadPort history;

	IssueHistoryService(IssueHistoryReadPort history) {
		this.history = history;
	}

	@Override
	public IssueHistory history(UUID issueId) {
		return history.history(issueId);
	}
}
