package com.civicrepairs.application.port.in;

/** Filing errors that inbound adapters turn into HTTP responses. Messages are in Spanish, safe to show. */
public class PetitionException extends RuntimeException {

	public enum Code {
		INVALID_PROFILE,
		PROFILE_REQUIRED,
		POLICY_OUTDATED,
		ISSUE_NOT_FOUND,
		PETITION_NOT_FOUND,
		ALREADY_FILED,
		NOT_FILEABLE
	}

	private final Code code;

	public PetitionException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return code;
	}
}
