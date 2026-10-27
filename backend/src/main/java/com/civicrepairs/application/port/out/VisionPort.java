package com.civicrepairs.application.port.out;

import com.civicrepairs.domain.shared.DamageType;

/** SAM 3.1 through vision-service: damage segmentation plus anonymization. */
public interface VisionPort {

	/** @throws ModelUnavailableException when the service fails or the budget is exhausted */
	VisionResult analyze(MediaContent photo, DamageType damageType);

	/**
	 * @param areaFraction share of the image covered by the damage
	 * @param blurredJpeg  faces and plates blurred: the only version that may be published
	 * @param markedJpeg   blurred photo with the damage highlighted
	 */
	record VisionResult(double areaFraction, int detections, int faces, int plates, byte[] blurredJpeg,
			byte[] markedJpeg) {
	}
}
