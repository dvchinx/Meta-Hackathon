package com.civicrepairs.application.port.out;

/** Personal data cannot be encrypted or decrypted: PERSONAL_DATA_KEY is missing or is not the key it was saved with. */
public class PersonalDataUnavailableException extends RuntimeException {

	public PersonalDataUnavailableException(String message) {
		super(message);
	}

	public PersonalDataUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
