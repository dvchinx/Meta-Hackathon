package com.civicrepairs.application.service;

import com.civicrepairs.domain.report.ReportChannel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Phone numbers (and web session tokens) are only ever stored as a salted HMAC. */
public final class ReporterIds {

	private final SecretKeySpec key;

	public ReporterIds(String salt) {
		if (Objects.requireNonNull(salt, "salt").length() < 16) {
			throw new IllegalArgumentException("PHONE_HASH_SALT must be at least 16 characters");
		}
		this.key = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	/** Web citizens are identified by their account id, hashed like a phone number. */
	public String forUser(UUID userId) {
		return hash(ReportChannel.WEB, userId.toString());
	}

	public String hash(ReportChannel channel, String rawId) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] digest = mac.doFinal((channel + ":" + rawId.strip()).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
