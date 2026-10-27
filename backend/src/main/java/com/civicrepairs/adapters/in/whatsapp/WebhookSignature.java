package com.civicrepairs.adapters.in.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Validates Meta's {@code X-Hub-Signature-256: sha256=<hex HMAC of the raw body with the app secret>}. */
final class WebhookSignature {

	private static final String PREFIX = "sha256=";

	private WebhookSignature() {
	}

	static boolean isValid(String appSecret, byte[] body, String header) {
		if (appSecret == null || appSecret.isBlank() || header == null || !header.startsWith(PREFIX)) {
			return false;
		}
		byte[] expected;
		byte[] received;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			expected = mac.doFinal(body);
			received = HexFormat.of().parseHex(header.substring(PREFIX.length()));
		} catch (IllegalArgumentException e) {
			return false;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
		return MessageDigest.isEqual(expected, received);
	}

	static String sign(String appSecret, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
