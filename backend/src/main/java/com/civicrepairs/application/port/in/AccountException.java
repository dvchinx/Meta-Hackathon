package com.civicrepairs.application.port.in;

/** Account errors that inbound adapters turn into HTTP responses. */
public class AccountException extends RuntimeException {

	public enum Code {
		INVALID_INPUT,
		EMAIL_TAKEN,
		BAD_CREDENTIALS,
		INVALID_TOKEN,
		EMAIL_NOT_VERIFIED,
		UNAUTHENTICATED
	}

	private final Code code;

	public AccountException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return code;
	}
}
