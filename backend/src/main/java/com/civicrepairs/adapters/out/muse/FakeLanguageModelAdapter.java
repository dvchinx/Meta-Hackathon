package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.domain.issue.AgencyResponse;
import com.civicrepairs.domain.issue.AgencyResponseClassification;
import com.civicrepairs.domain.issue.RepairVerdict;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Offline stand-in for Muse Spark (META_API_ENABLED=false): deterministic, free. */
@Component
@ConditionalOnProperty(name = "civic.meta.enabled", havingValue = "false", matchIfMissing = true)
class FakeLanguageModelAdapter implements LanguageModelPort {

	private static final Pattern DONE = Pattern.compile("repar|arregl|tapad|reemplaz|interven|podad|retirad|recogid");
	private static final Pattern RECEIPT = Pattern.compile("hemos recibido|recibimos su|radicad");

	@Override
	public Interpretation interpret(MediaContent photo, String context) {
		return new Interpretation(DamageType.POTHOLE, Urgency.MEDIUM, true, List.of(),
				"Hueco en la vía (respuesta simulada, Muse Spark desactivado).", 0.9, AssetOwner.UNKNOWN);
	}

	@Override
	public SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate) {
		return new SameDamageVerdict(existing.media().sha256().equals(candidate.media().sha256()), 0.9);
	}

	/** Keyword guess, good enough to drive the admin panel offline. */
	@Override
	public AgencyResponse.Reading classifyAgencyResponse(String body) {
		String text = body.toLowerCase(Locale.ROOT);
		var classification = DONE.matcher(text).find() ? AgencyResponseClassification.RESOLVES
				: RECEIPT.matcher(text).find() ? AgencyResponseClassification.NO_RESPONSE
				: AgencyResponseClassification.EVASIVE;
		return new AgencyResponse.Reading(classification, "Lectura simulada (Muse Spark desactivado).", 0.9);
	}

	/** Always "still there": offline, the fake vision cannot tell photos apart, so it never claims a repair. */
	@Override
	public RepairVerdict judgeRepair(MediaContent before, MediaContent after, DamageType damageType) {
		return new RepairVerdict(RepairVerdict.Judgement.NOT_REPAIRED, 0.9,
				"Comparación simulada (Muse Spark desactivado): el daño sigue visible.");
	}
}
