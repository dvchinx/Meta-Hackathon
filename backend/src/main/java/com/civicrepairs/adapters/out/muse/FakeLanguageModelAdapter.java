package com.civicrepairs.adapters.out.muse;

import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaContent;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Offline stand-in for Muse Spark (META_API_ENABLED=false): deterministic, free. */
@Component
@ConditionalOnProperty(name = "civic.meta.enabled", havingValue = "false", matchIfMissing = true)
class FakeLanguageModelAdapter implements LanguageModelPort {

	@Override
	public Interpretation interpret(MediaContent photo, String context) {
		return new Interpretation(DamageType.POTHOLE, Urgency.MEDIUM, true, List.of(),
				"Hueco en la vía (respuesta simulada, Muse Spark desactivado).", 0.9, AssetOwner.UNKNOWN);
	}

	@Override
	public SameDamageVerdict sameDamage(MediaContent existing, MediaContent candidate) {
		return new SameDamageVerdict(existing.media().sha256().equals(candidate.media().sha256()), 0.9);
	}
}
