package com.civicrepairs.application.port.in;

/**
 * How one agency handles the damage assigned to it. Counts are Issues, except the responses.
 *
 * @param averageDaysOpen   mean age of the open Issues, null when none is open
 * @param overduePetitions  latest petition past its legal deadline with no reply since it was filed
 * @param evasiveResponses  replies Muse Spark read as evasive or as no real answer
 * @param repairClaims      Issues the agency said it repaired at least once
 * @param claimsConfirmed   of those, repairs a new photo confirmed (VERIFIED)
 * @param claimsRefuted     Issues reopened because a new photo showed the damage still there
 * @param examples          demo examples among the Issues (see infra/seed)
 */
public record AgencyScorecard(String agencyCode, String agencyName, int issues, int open, int verified,
		Double averageDaysOpen, int filed, int overduePetitions, int resolvingResponses, int evasiveResponses,
		int repairClaims, int claimsConfirmed, int claimsRefuted, int examples) {
}
