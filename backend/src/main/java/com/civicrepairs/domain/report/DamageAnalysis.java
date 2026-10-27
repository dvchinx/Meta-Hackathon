package com.civicrepairs.domain.report;

import java.util.Objects;
import java.util.UUID;

/**
 * SAM 3.1 output for the report photo.
 *
 * @param areaFraction  share of the image covered by the damage (union of all instance masks)
 * @param publicMediaId blurred photo (faces and plates), the only one the public ever sees
 * @param markedMediaId blurred photo with the damage highlighted, sent back for confirmation
 */
public record DamageAnalysis(double areaFraction, int detections, UUID publicMediaId, UUID markedMediaId) {

	public DamageAnalysis {
		Objects.requireNonNull(publicMediaId, "publicMediaId");
		Objects.requireNonNull(markedMediaId, "markedMediaId");
	}
}
