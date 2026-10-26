package com.civicrepairs.domain.shared;

/**
 * Where reports are accepted. The MVP only covers Bogotá.
 * The box is a rough envelope of the Distrito Capital (urban area plus Sumapaz);
 * points outside it are rejected before any model call.
 */
public enum ServiceArea {
	BOGOTA(3.72, 4.84, -74.46, -73.98);

	private final double minLatitude;
	private final double maxLatitude;
	private final double minLongitude;
	private final double maxLongitude;

	ServiceArea(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
		this.minLatitude = minLatitude;
		this.maxLatitude = maxLatitude;
		this.minLongitude = minLongitude;
		this.maxLongitude = maxLongitude;
	}

	public boolean contains(GeoPoint point) {
		return point.latitude() >= minLatitude && point.latitude() <= maxLatitude
				&& point.longitude() >= minLongitude && point.longitude() <= maxLongitude;
	}
}
