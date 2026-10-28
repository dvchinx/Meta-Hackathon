package com.civicrepairs.domain.petition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilerProfileTest {

	@Test
	void normalizesWhatPeopleType() {
		var p = new FilerProfile(IdentificationType.CC, " 1.020.304.050 ", "  María   José ", "Pérez  Gómez",
				"+57 300 123 4567", "  Calle 72 # 10-20  ");

		assertThat(p.idNumber()).isEqualTo("1020304050");
		assertThat(p.firstNames()).isEqualTo("María José");
		assertThat(p.fullName()).isEqualTo("María José Pérez Gómez");
		assertThat(p.mobilePhone()).isEqualTo("3001234567");
		assertThat(p.address()).isEqualTo("Calle 72 # 10-20");
	}

	@Test
	void passportsAreAlphanumericAndAddressIsOptional() {
		var p = new FilerProfile(IdentificationType.PA, "ab123456", "Ana", "O'Neil", "3001234567", " ");

		assertThat(p.idNumber()).isEqualTo("AB123456");
		assertThat(p.address()).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"CC, 12AB, Ana, Ruiz, 3001234567, idNumber",
			"CC, 12, Ana, Ruiz, 3001234567, idNumber",
			"PA, ab, Ana, Ruiz, 3001234567, idNumber",
			"CC, 1020304050, '', Ruiz, 3001234567, firstNames",
			"CC, 1020304050, Ana2, Ruiz, 3001234567, firstNames",
			"CC, 1020304050, Ana, '', 3001234567, lastNames",
			"CC, 1020304050, Ana, Ruiz, 6011234567, mobilePhone",
			"CC, 1020304050, Ana, Ruiz, 300123, mobilePhone",
	})
	void namesTheInvalidField(IdentificationType type, String number, String first, String last, String mobile,
			String field) {
		assertThatThrownBy(() -> new FilerProfile(type, number, first, last, mobile, null))
				.isInstanceOfSatisfying(InvalidFilerProfileException.class, e -> assertThat(e.field()).isEqualTo(field));
	}

	@Test
	void requiresTheDocumentType() {
		assertThatThrownBy(() -> new FilerProfile(null, "1020304050", "Ana", "Ruiz", "3001234567", null))
				.isInstanceOfSatisfying(InvalidFilerProfileException.class, e -> assertThat(e.field()).isEqualTo("idType"));
	}
}
