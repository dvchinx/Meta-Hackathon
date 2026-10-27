package com.civicrepairs.adapters.out.muse;

class InvalidModelOutputException extends RuntimeException {

	InvalidModelOutputException(String message) {
		super(message);
	}

	InvalidModelOutputException(String message, Throwable cause) {
		super(message, cause);
	}
}
