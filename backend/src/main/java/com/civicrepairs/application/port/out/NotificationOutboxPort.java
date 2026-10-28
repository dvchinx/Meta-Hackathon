package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.issue.IssueStatus;

import java.util.List;
import java.util.UUID;

/**
 * Status changes waiting to be notified. Rows are written in the same transaction as the Issue (so none is lost)
 * and delivered at least once by a background job.
 */
public interface NotificationOutboxPort {

	List<StatusChange> pending(int limit);

	void markSent(long id);

	record StatusChange(long id, UUID issueId, IssueStatus status) {
	}
}
