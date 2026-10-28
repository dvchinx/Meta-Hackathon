package com.civicrepairs.domain.account;

/** Short and clear: length is what matters; composition rules only push people to reuse passwords. */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 128;

	private PasswordPolicy() {
	}

	public static boolean isAcceptable(String rawPassword) {
		return rawPassword != null && rawPassword.length() >= MIN_LENGTH && rawPassword.length() <= MAX_LENGTH
				&& !rawPassword.isBlank();
	}
}
