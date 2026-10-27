package com.civicrepairs.domain.report;

public class InvalidReportStateException extends RuntimeException {

	public InvalidReportStateException(String message) {
		super(message);
	}
}
