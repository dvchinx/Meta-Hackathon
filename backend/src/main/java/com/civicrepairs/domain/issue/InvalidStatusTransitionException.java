package com.civicrepairs.domain.issue;

public class InvalidStatusTransitionException extends RuntimeException {

	public InvalidStatusTransitionException(IssueStatus from, IssueStatus to) {
		super("Issue cannot go from " + from + " to " + to);
	}

	public InvalidStatusTransitionException(String message) {
		super(message);
	}
}
