package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.IssueHistory;

import java.util.UUID;

public interface IssueHistoryReadPort {

	/** Empty lists for an unknown Issue. */
	IssueHistory history(UUID issueId);
}
