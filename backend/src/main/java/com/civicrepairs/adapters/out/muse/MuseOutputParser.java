package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.application.port.out.LanguageModelPort.SameDamageVerdict;
import com.civicrepairs.domain.issue.AgencyResponse;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.RepairVerdict;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates model output against the prompt's schema on our side too: we never trust that the API enforced it.
 * Any violation throws, which triggers the single retry and then manual review.
 */
final class MuseOutputParser {

	private static final Set<String> INTERPRETATION_FIELDS =
			Set.of("damageType", "assetOwner", "urgency", "isValidUrbanDamage", "locationHints", "summary", "confidence");
	private static final Set<String> SAME_DAMAGE_FIELDS = Set.of("sameDamage", "confidence", "reason");
	private static final Set<String> AGENCY_RESPONSE_FIELDS = Set.of("classification", "summary", "confidence");
	private static final Set<String> REPAIR_FIELDS = Set.of("sameLocation", "damageSpotVisible", "judgement", "confidence",
			"reason");
	static final int MAX_SUMMARY = 280;
	static final int MAX_REASON = 280;
	static final int MAX_HINTS = 5;

	private final JsonMapper json;

	MuseOutputParser(JsonMapper json) {
		this.json = json;
	}

	Interpretation interpretation(String content) {
		JsonNode node = object(content, INTERPRETATION_FIELDS);
		String summary = text(node, "summary").strip();
		if (summary.isEmpty() || summary.length() > MAX_SUMMARY) {
			throw new InvalidModelOutputException("summary length " + summary.length());
		}
		JsonNode hints = node.get("locationHints");
		if (!hints.isArray() || hints.size() > MAX_HINTS) {
			throw new InvalidModelOutputException("locationHints must be an array of at most " + MAX_HINTS);
		}
		List<String> hintList = new ArrayList<>();
		for (JsonNode hint : hints) {
			if (!hint.isString()) {
				throw new InvalidModelOutputException("locationHints must contain strings");
			}
			hintList.add(hint.asString().strip());
		}
		return new Interpretation(
				enumValue(DamageType.class, text(node, "damageType")),
				enumValue(Urgency.class, text(node, "urgency")),
				bool(node, "isValidUrbanDamage"),
				hintList,
				summary,
				probability(node, "confidence"),
				enumValue(AssetOwner.class, text(node, "assetOwner")));
	}

	SameDamageVerdict sameDamage(String content) {
		JsonNode node = object(content, SAME_DAMAGE_FIELDS);
		text(node, "reason");
		return new SameDamageVerdict(bool(node, "sameDamage"), probability(node, "confidence"));
	}

	AgencyResponse.Reading agencyResponse(String content) {
		JsonNode node = object(content, AGENCY_RESPONSE_FIELDS);
		return new AgencyResponse.Reading(
				enumValue(AgencyResponseClassification.class, text(node, "classification")),
				boundedText(node, "summary", MAX_SUMMARY),
				probability(node, "confidence"));
	}

	/**
	 * A different place, or a photo that does not show where the damage was, is never a verdict on the repair,
	 * whatever the model says.
	 */
	RepairVerdict repair(String content) {
		JsonNode node = object(content, REPAIR_FIELDS);
		var judgement = enumValue(RepairVerdict.Judgement.class, text(node, "judgement"));
		boolean sameLocation = bool(node, "sameLocation");
		boolean spotVisible = bool(node, "damageSpotVisible");
		if (!sameLocation || !spotVisible) {
			judgement = RepairVerdict.Judgement.INCONCLUSIVE;
		}
		return new RepairVerdict(judgement, probability(node, "confidence"), boundedText(node, "reason", MAX_REASON));
	}

	private static String boundedText(JsonNode node, String field, int max) {
		String value = text(node, field).strip();
		if (value.isEmpty() || value.length() > max) {
			throw new InvalidModelOutputException(field + " length " + value.length());
		}
		return value;
	}

	private JsonNode object(String content, Set<String> fields) {
		if (content == null || content.isBlank()) {
			throw new InvalidModelOutputException("empty content");
		}
		JsonNode node;
		try {
			node = json.readTree(content);
		} catch (JacksonException e) {
			throw new InvalidModelOutputException("not JSON", e);
		}
		if (!node.isObject()) {
			throw new InvalidModelOutputException("not a JSON object");
		}
		Set<String> actual = new HashSet<>(node.propertyNames());
		if (!actual.equals(fields)) {
			throw new InvalidModelOutputException("fields " + actual + ", expected " + fields);
		}
		return node;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (!value.isString()) {
			throw new InvalidModelOutputException(field + " must be a string");
		}
		return value.asString();
	}

	private static boolean bool(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (!value.isBoolean()) {
			throw new InvalidModelOutputException(field + " must be a boolean");
		}
		return value.asBoolean();
	}

	private static double probability(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (!value.isNumber() || value.asDouble() < 0 || value.asDouble() > 1) {
			throw new InvalidModelOutputException(field + " must be a number in [0, 1]");
		}
		return value.asDouble();
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new InvalidModelOutputException("unknown " + type.getSimpleName() + " " + value);
		}
	}
}
