package com.civicrepairs.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Session and email-link secrets: 256 random bits, URL-safe; only their SHA-256 is ever stored. */
final class Secrets {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Secrets() {
	}

	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static boolean looksValid(String secret) {
		return secret != null && secret.length() >= 32 && secret.length() <= 64 && secret.matches("[A-Za-z0-9_-]+");
	}
}
