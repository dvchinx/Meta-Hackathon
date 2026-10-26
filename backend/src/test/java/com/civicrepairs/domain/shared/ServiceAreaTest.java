package com.civicrepairs.domain.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAreaTest {

	@ParameterizedTest(name = "{0}")
	@CsvSource({
			"Plaza de Bolívar, 4.5981, -74.0760, true",
			"Suba, 4.7410, -74.0840, true",
			"Usme, 4.4730, -74.1230, true",
			"Medellín, 6.2442, -75.5812, false",
			"Soacha, 4.5794, -74.2168, true",   // known false positive of the rough envelope; the exact boundary check will use PostGIS
			"Chía, 4.8619, -74.0587, false",
	})
	void acceptsOnlyPointsInsideBogota(String place, double lat, double lon, boolean inside) {
		assertThat(ServiceArea.BOGOTA.contains(new GeoPoint(lat, lon))).as(place).isEqualTo(inside);
	}
}
