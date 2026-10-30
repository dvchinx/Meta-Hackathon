package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.RepairVerdict.Judgement;
import com.civicrepairs.domain.shared.AssetOwner;
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
			{"damageType":"OPEN_MANHOLE","assetOwner":"POWER","urgency":"HIGH","isValidUrbanDamage":true,
			 "locationHints":["Calle 72 con Carrera 7"],"summary":"Hueco profundo en el carril derecho.","confidence":0.87}
			""";

	@Test
	void parsesAValidInterpretation() {
		var i = parser.interpretation(VALID);

		assertThat(i.damageType()).isEqualTo(DamageType.OPEN_MANHOLE);
		assertThat(i.assetOwner()).isEqualTo(AssetOwner.POWER);
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
			// v1 output, without assetOwner
			"{\"damageType\":\"POTHOLE\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			// unknown asset owner
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"EAAB\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			// missing confidence
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\"}",
			// extra field
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5,\"extra\":1}",
			// unknown damage type
			"{\"damageType\":\"GRAFFITI\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			// confidence out of range
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"x\",\"confidence\":1.5}",
			// wrong types
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":\"yes\",\"locationHints\":[],\"summary\":\"x\",\"confidence\":0.5}",
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":\"Calle 72\",\"summary\":\"x\",\"confidence\":0.5}",
			// blank summary
			"{\"damageType\":\"POTHOLE\",\"assetOwner\":\"UNKNOWN\",\"urgency\":\"HIGH\",\"isValidUrbanDamage\":true,\"locationHints\":[],\"summary\":\"  \",\"confidence\":0.5}",
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

	@Test
	void readsAgencyResponses() {
		var reading = parser.agencyResponse("""
				{"classification":"RESOLVES","summary":"La entidad dice que tapó el hueco.","confidence":0.8}
				""");

		assertThat(reading.classification()).isEqualTo(AgencyResponseClassification.RESOLVES);
		assertThat(reading.summary()).isEqualTo("La entidad dice que tapó el hueco.");
		assertThatThrownBy(() -> parser.agencyResponse("{\"classification\":\"MAYBE\",\"summary\":\"x\",\"confidence\":0.5}"))
				.isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void aDifferentPlaceIsNeverAVerdictOnTheRepair() {
		var verdict = parser.repair("""
				{"sameLocation":false,"damageSpotVisible":true,"judgement":"REPAIRED","confidence":0.95,
				"reason":"Otra calle."}
				""");

		assertThat(verdict.judgement()).isEqualTo(Judgement.INCONCLUSIVE);
		assertThat(parser.repair("""
				{"sameLocation":true,"damageSpotVisible":true,"judgement":"NOT_REPAIRED","confidence":0.8,
				"reason":"El hueco sigue ahí."}
				""").judgement()).isEqualTo(Judgement.NOT_REPAIRED);
		assertThatThrownBy(() -> parser.repair("""
				{"sameLocation":true,"damageSpotVisible":true,"judgement":"REPAIRED","confidence":0.8}
				""")).isInstanceOf(InvalidModelOutputException.class);
	}

	@Test
	void aPhotoThatMissesTheDamageSpotIsNeverAVerdictEither() {
		// Same corner, but the drain is out of frame: an empty street proves nothing.
		var verdict = parser.repair("""
				{"sameLocation":true,"damageSpotVisible":false,"judgement":"REPAIRED","confidence":0.75,
				"reason":"No se ve el sumidero."}
				""");

		assertThat(verdict.judgement()).isEqualTo(Judgement.INCONCLUSIVE);
		assertThatThrownBy(() -> parser.repair("""
				{"sameLocation":true,"judgement":"REPAIRED","confidence":0.8,"reason":"Tapado."}
				""")).isInstanceOf(InvalidModelOutputException.class);
	}
}
