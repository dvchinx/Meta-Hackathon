package com.civicrepairs.domain.shared;

/** WGS84 coordinate (SRID 4326). */
public record GeoPoint(double latitude, double longitude) {

	public GeoPoint {
		if (latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("latitude out of range: " + latitude);
		}
		if (longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("longitude out of range: " + longitude);
		}
	}
}
