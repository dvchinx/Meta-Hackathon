package com.civicrepairs.adapters.out.persistence;

import com.civicrepairs.application.port.out.PersonalDataUnavailableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDataCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
	private static final byte[] CEDULA = "1020304050".getBytes(StandardCharsets.UTF_8);

	@Test
	void roundTripsWithoutLeavingPlaintext() {
		var cipher = new PersonalDataCipher(KEY);

		byte[] sealed = cipher.encrypt(CEDULA, "filer_profile:a");

		assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("1020304050");
		assertThat(cipher.decrypt(sealed, "filer_profile:a")).isEqualTo(CEDULA);
		assertThat(cipher.encrypt(CEDULA, "filer_profile:a")).isNotEqualTo(sealed);
	}

	@Test
	void aCiphertextMovedToAnotherRowDoesNotDecrypt() {
		var cipher = new PersonalDataCipher(KEY);
		byte[] sealed = cipher.encrypt(CEDULA, "filer_profile:a");

		assertThatThrownBy(() -> cipher.decrypt(sealed, "filer_profile:b"))
				.isInstanceOf(PersonalDataUnavailableException.class);
	}

	@Test
	void anotherKeyCannotRead() {
		byte[] sealed = new PersonalDataCipher(KEY).encrypt(CEDULA, "x");
		var other = new PersonalDataCipher(Base64.getEncoder().encodeToString("k".repeat(32).getBytes()));

		assertThatThrownBy(() -> other.decrypt(sealed, "x")).isInstanceOf(PersonalDataUnavailableException.class);
	}

	@Test
	void withoutAKeyNothingIsStored() {
		var cipher = new PersonalDataCipher("");

		assertThatThrownBy(() -> cipher.encrypt(CEDULA, "x")).isInstanceOf(PersonalDataUnavailableException.class);
	}

	@Test
	void rejectsKeysOfTheWrongSize() {
		assertThatThrownBy(() -> new PersonalDataCipher(Base64.getEncoder().encodeToString(new byte[16])))
				.isInstanceOf(IllegalStateException.class);
	}
}
