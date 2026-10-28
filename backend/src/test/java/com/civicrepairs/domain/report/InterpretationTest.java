package com.civicrepairs.domain.report;

import com.civicrepairs.domain.shared.AssetOwner;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.Urgency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretationTest {

	private static Interpretation of(DamageType type, Urgency urgency, AssetOwner owner) {
		return new Interpretation(type, urgency, true, List.of(), "Descripción", 0.9, owner);
	}

	@Test
	void onlyUrgentHazardsAskToCall123() {
		assertThat(of(DamageType.OPEN_MANHOLE, Urgency.HIGH, null).requiresEmergencyCall()).isTrue();
		assertThat(of(DamageType.RISKY_TREE, Urgency.HIGH, null).requiresEmergencyCall()).isTrue();
		assertThat(of(DamageType.OPEN_MANHOLE, Urgency.MEDIUM, null).requiresEmergencyCall()).isFalse();
		assertThat(of(DamageType.GARBAGE_DUMP, Urgency.HIGH, null).requiresEmergencyCall()).isFalse();
	}

	@Test
	void interpretationsWithoutAnOwnerAreUnknown() {
		assertThat(of(DamageType.POTHOLE, Urgency.LOW, null).assetOwner()).isEqualTo(AssetOwner.UNKNOWN);
	}
}
