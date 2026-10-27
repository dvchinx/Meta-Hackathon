package com.civicrepairs.application.port.out;

/** A model could not give a usable answer. The caller falls back (usually to manual review). */
public class ModelUnavailableException extends RuntimeException {

	public enum Reason {
		BUDGET_EXHAUSTED,
		INVALID_OUTPUT,
		ERROR
	}

	private final Reason reason;

	public ModelUnavailableException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public ModelUnavailableException(Reason reason, String message) {
		this(reason, message, null);
	}

	public Reason reason() {
		return reason;
	}
}
