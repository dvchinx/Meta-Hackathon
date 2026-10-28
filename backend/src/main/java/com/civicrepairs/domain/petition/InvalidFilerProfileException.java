package com.civicrepairs.domain.petition;

/** A field of the filer profile is missing or malformed. {@link #field()} names it. */
public class InvalidFilerProfileException extends IllegalArgumentException {

	private final String field;

	public InvalidFilerProfileException(String field) {
		super("invalid " + field);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
