package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AnalyzeReportUseCase;
import com.civicrepairs.application.port.in.ReportNotFoundException;
import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.application.port.out.VisionPort;
import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import com.civicrepairs.domain.report.AnalysisOutcome;
import com.civicrepairs.domain.report.DamageAnalysis;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Model calls are slow and cost money, so they run outside any database transaction. */
@Service
class AnalyzeReportService implements AnalyzeReportUseCase {

	private static final Logger log = LoggerFactory.getLogger(AnalyzeReportService.class);

	private final ReportRepositoryPort reports;
	private final MediaStoragePort media;
	private final LanguageModelPort languageModel;
	private final VisionPort vision;
	private final AgencyRepositoryPort agencies;
	private final TransactionTemplate tx;

	AnalyzeReportService(ReportRepositoryPort reports, MediaStoragePort media, LanguageModelPort languageModel,
			VisionPort vision, AgencyRepositoryPort agencies, TransactionTemplate tx) {
		this.reports = reports;
		this.media = media;
		this.languageModel = languageModel;
		this.vision = vision;
		this.agencies = agencies;
		this.tx = tx;
	}

	@Override
	public AnalysisResult analyze(UUID reportId) {
		Report report = tx.execute(status -> {
			Report r = load(reportId);
			r.startAnalysis();
			reports.save(r);
			return r;
		});

		var photo = media.load(report.photoMediaId().orElseThrow());
		AnalysisOutcome outcome = null;
		try {
			Interpretation interpretation = languageModel.interpret(photo, report.context().orElse(null));
			DamageAnalysis analysis = null;
			if (interpretation.isAcceptable()) {
				var result = vision.analyze(photo, interpretation.damageType());
				UUID photoId = photo.media().id();
				var blurred = media.store(result.blurredJpeg(), "image/jpeg", MediaKind.PHOTO, Visibility.PUBLIC, photoId);
				var marked = media.store(result.markedJpeg(), "image/jpeg", MediaKind.PHOTO, Visibility.PUBLIC, photoId);
				analysis = new DamageAnalysis(result.areaFraction(), result.detections(), blurred.id(), marked.id());
			}
			outcome = report.completeAnalysis(interpretation, analysis);
		} catch (ModelUnavailableException e) {
			log.warn("Report {} sent to manual review: {} ({})", reportId, e.reason(), e.getMessage());
			report.sendToManualReview("models unavailable: " + e.reason());
		}

		tx.executeWithoutResult(status -> reports.save(report));

		Interpretation interpretation = report.interpretation().orElse(null);
		String agencyName = interpretation == null ? null
				: agencies.findResponsibleFor(interpretation.damageType()).map(Agency::name).orElse(null);
		UUID marked = outcome == AnalysisOutcome.AWAITING_CONFIRMATION
				? report.analysis().map(DamageAnalysis::markedMediaId).orElse(null)
				: null;
		return new AnalysisResult(reportId, outcome, interpretation, marked, agencyName);
	}

	private Report load(UUID id) {
		return reports.findById(id).orElseThrow(() -> new ReportNotFoundException(id));
	}
}
