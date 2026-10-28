package com.civicrepairs.domain.petition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A derecho de petición about an Issue, filed by a citizen with their own data. Filing is simulated: the radicado
 * is ours ({@code SIM-}) and nothing reaches the agency, but the document is what the agency would receive.
 *
 * @param subject       what the petition is about (the confirmed description of the damage)
 * @param policyVersion version of the data policy the citizen accepted when sharing their data with the agency
 */
public record Petition(
		UUID id,
		UUID issueId,
		String agencyCode,
		UUID filerUserId,
		String radicado,
		Instant filedAt,
		LocalDate dueDate,
		Petitioner petitioner,
		String subject,
		String policyVersion) {

	public Petition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(issueId, "issueId");
		Objects.requireNonNull(agencyCode, "agencyCode");
		Objects.requireNonNull(radicado, "radicado");
		Objects.requireNonNull(filedAt, "filedAt");
		Objects.requireNonNull(dueDate, "dueDate");
		Objects.requireNonNull(petitioner, "petitioner");
		Objects.requireNonNull(subject, "subject");
		Objects.requireNonNull(policyVersion, "policyVersion");
	}

	public static Petition file(UUID id, UUID issueId, String agencyCode, UUID filerUserId, long sequence,
			Instant at, Petitioner petitioner, String subject, String policyVersion) {
		LocalDate filedOn = LocalDate.ofInstant(at, ResponseDeadline.BOGOTA);
		return new Petition(id, issueId, agencyCode, filerUserId, radicado(filedOn.getYear(), sequence), at,
				ResponseDeadline.dueDate(filedOn), petitioner, subject, policyVersion);
	}

	/** Simulated filing number, e.g. {@code SIM-2026-000042}. */
	static String radicado(int year, long sequence) {
		if (sequence <= 0) {
			throw new IllegalArgumentException("sequence must be positive");
		}
		return "SIM-%d-%06d".formatted(year, sequence);
	}
}
