package com.civicrepairs.support;

import com.civicrepairs.application.port.out.GeoQueryPort;
import com.civicrepairs.domain.shared.DamageType;
import com.civicrepairs.domain.shared.GeoPoint;
import com.civicrepairs.domain.shared.Locality;
import com.civicrepairs.domain.shared.ServiceArea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Without PostGIS: every point inside Bogotá's box is in Chapinero, and there are no Issues nearby. */
public class FakeGeo implements GeoQueryPort {

	public static final Locality CHAPINERO = new Locality("02", "Chapinero");

	@Override
	public List<UUID> findOpenIssuesNear(GeoPoint point, double meters, DamageType damageType, int limit) {
		return List.of();
	}

	@Override
	public Optional<Locality> findLocality(GeoPoint point) {
		return ServiceArea.BOGOTA.contains(point) ? Optional.of(CHAPINERO) : Optional.empty();
	}
}
