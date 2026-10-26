package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;

import java.util.List;
import java.util.UUID;

public interface GeoQueryPort {

	/** Open (not VERIFIED) Issues of the same type within {@code meters}, nearest first. Dedup candidates. */
	List<UUID> findOpenIssuesNear(GeoPoint point, double meters, DamageType damageType, int limit);
}
