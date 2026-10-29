package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;

import java.time.Instant;
import java.util.UUID;

/**
 * What the public map may know about an Issue. Never contains reporter or petitioner data.
 *
 * @param localityName null for Issues outside the locality boundaries (only possible for data older than them)
 * @param channel      null when no official channel is known for the damage type
 * @param petition     latest petition, null until one is filed
 * @param example      null for real Issues; set on the demo examples loaded by infra/seed
 */
public record PublicIssue(
		UUID id,
		DamageType damageType,
		IssueStatus status,
		double latitude,
		double longitude,
		int reportCount,
		Instant openedAt,
		String agencyName,
		UUID coverMediaId,
		String localityName,
		OfficialChannel channel,
		FiledPetition petition,
		ExampleNote example) {
}
