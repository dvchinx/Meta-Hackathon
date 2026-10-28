package com.civicrepairs.application.port.in;

import com.civicrepairs.domain.report.AnalysisOutcome;
import com.civicrepairs.domain.report.Interpretation;

import java.util.UUID;

/** Muse Spark interpretation + SAM 3.1 segmentation and anonymization. Slow: run it off the request thread. */
public interface AnalyzeReportUseCase {

	AnalysisResult analyze(UUID reportId);

	/**
	 * @param outcome        null when the models were unavailable and the report went to manual review
	 * @param markedMediaId  blurred photo with the damage highlighted (null unless awaiting confirmation)
	 * @param agencyName     responsible agency, if any rule covers the damage type
	 * @param localityName   localidad of the report's location
	 */
	record AnalysisResult(UUID reportId, AnalysisOutcome outcome, Interpretation interpretation, UUID markedMediaId,
			String agencyName, String localityName) {
	}
}
