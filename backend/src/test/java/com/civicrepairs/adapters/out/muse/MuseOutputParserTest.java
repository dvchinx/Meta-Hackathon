package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MuseOutputParserTest {

	private final MuseOutputParser parser = new MuseOutputParser(JsonMapper.builder().build());

	static final String VALID = """
			{"damageType":"POTHOLE","urgency":"HIGH","isValidUrbanDamage":true,
			 "locationHints":["Calle 72 con Carrera 7"],"summary":"Hueco profundo en el carril derecho.","confidence":0.87}
			""";

	@Test
	void parsesAValidInterpretation() {
		var i = parser.interpretation(VALID);

		assertThat(i.damageType()).isEqualTo(DamageType.POTHOLE);
		assertThat(i.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(i.validUrbanDamage()).isTrue();
		assertThat(i.locationHints()).containsExactly("Calle 72 con Carrera 7");
		assertThat(i.confidence()).isEqualTo(0.87);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"not json",
			"[1,2]",
			// missing confidence
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\"}",
			// extra field
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5,\"extra\":1}",
			// unknown damage type
			"{\"damageType\":\"GRAFFITI\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			// confidence out of range
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":1.5}",
			// wrong types
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":\"yes\",\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":\"Calle 72\",\"summary\":\"x\",\"confidence\":0.5}",
			// blank summary
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"  \",\"confidence\":0.5}",
	})
	void rejectsAnythingOutsideTheSchema(String content) {
		assertThatThrownBy(() -> parser.interpretation(content)).isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void rejectsOverlongSummaries() {
		String longSummary = "a".repeat(MuseOutputParser.MAX_SUMMARY + 1);
		String content = VALID.replace("Hueco profundo en el carril derecho.", longSummary);

		assertThatThrownBy(() -> parser.interpretation(content)).isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void parsesASameDamageVerdict() {
		var verdict = parser.sameDamage("{\"sameDamage\":true,\"confidence\":0.8,\"reason\":\"Mismo andén y bordillo.\"}");

		assertThat(verdict.sameDamage()).isTrue();
		assertThat(verdict.confidence()).isEqualTo(0.8);
	}
}
