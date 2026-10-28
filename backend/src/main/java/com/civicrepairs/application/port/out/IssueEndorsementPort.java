package com.civicrepairs.application.port.out;

import java.util.UUID;

public interface IssueEndorsementPort {

	/** @return false when this reporter already endorsed or reported the Issue */
	boolean add(UUID issueId, String reporterId);
}
