package com.civicrepairs.application.port.in;

/** An action on an Issue that cannot happen now. Messages are in Spanish, safe to show. */
public class IssueActionException extends RuntimeException {

	public enum Code {
		NOT_FOUND,
		WRONG_STATUS,
		INVALID_INPUT,
		RATE_LIMITED,
		MODELS_UNAVAILABLE
	}

	private final Code code;

	public IssueActionException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return code;
	}
}
