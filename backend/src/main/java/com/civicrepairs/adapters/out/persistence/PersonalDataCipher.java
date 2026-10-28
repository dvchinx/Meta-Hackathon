package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.PersonalDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for the data citizens give us to file petitions (document number, names, phone). Layout:
 * {@code version (1 byte) | IV (12 bytes) | ciphertext + tag}. The row it belongs to is the associated data, so a
 * ciphertext copied into another row does not decrypt. Without PERSONAL_DATA_KEY nothing is stored or read.
 */
@Component
class PersonalDataCipher {

	private static final Logger log = LoggerFactory.getLogger(PersonalDataCipher.class);
	private static final byte VERSION = 1;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKey key;

	PersonalDataCipher(@Value("${civic.personal-data.key:}") String base64Key) {
		if (base64Key.isBlank()) {
			log.warn("PERSONAL_DATA_KEY is not set: filing data cannot be stored, so petitions are disabled.");
			this.key = null;
			return;
		}
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key.strip());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("PERSONAL_DATA_KEY must be base64 (openssl rand -base64 32)", e);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("PERSONAL_DATA_KEY must decode to 32 bytes (openssl rand -base64 32)");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	byte[] encrypt(byte[] plaintext, String context) {
		SecretKey k = requireKey();
		byte[] iv = new byte[IV_BYTES];
		RANDOM.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(plaintext);
			return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(VERSION).put(iv).put(sealed).array();
		} catch (GeneralSecurityException e) {
			throw new PersonalDataUnavailableException("encryption failed", e);
		}
	}

	byte[] decrypt(byte[] stored, String context) {
		SecretKey k = requireKey();
		if (stored.length <= 1 + IV_BYTES || stored[0] != VERSION) {
			throw new PersonalDataUnavailableException("unknown ciphertext format");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
		} catch (GeneralSecurityException e) {
			throw new PersonalDataUnavailableException("decryption failed: wrong PERSONAL_DATA_KEY or tampered data", e);
		}
	}

	private SecretKey requireKey() {
		if (key == null) {
			throw new PersonalDataUnavailableException("PERSONAL_DATA_KEY is not set");
		}
		return key;
	}
}
