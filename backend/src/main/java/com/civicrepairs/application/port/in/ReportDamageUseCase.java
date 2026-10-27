package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.report.MissingItem;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;
import com.civicrepairs.domain.shared.GeoPoint;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Receives report data in any order and in any combination; answers with what is still missing. */
public interface ReportDamageUseCase {

	SubmitResult submit(Submission submission);

	/**
	 * @param reporterId already hashed by the inbound adapter (see {@code ReporterIds})
	 * @param photo      null when this message carries no photo
	 * @param location   null when this message carries no location
	 */
	record Submission(ReportChannel channel, String reporterId, Photo photo, String text, GeoPoint location,
			Instant at) {
	}

	record Photo(byte[] data, String contentType) {
	}

	/**
	 * @param reportId null only when rate limited
	 */
	record SubmitResult(UUID reportId, ReportStatus status, Set<MissingItem> missing, boolean locationOutsideBogota,
			boolean rateLimited, boolean readyForAnalysis) {
	}
}
