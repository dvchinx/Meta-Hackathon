package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AnalyzeReportUseCase;
import com.civicrepairs.application.port.in.AnalyzeReportUseCase.AnalysisResult;
import com.civicrepairs.application.port.in.ConfirmReportUseCase;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.ConfirmReportUseCase.ConfirmationResult;
import com.civicrepairs.application.port.in.ReportDamageUseCase;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Submission;
import com.civicrepairs.application.service.ReporterIds;
import com.civicrepairs.domain.report.AnalysisOutcome;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.shared.GeoPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Web form. The signed-in account plays the role of the phone number: its id is hashed and only the owner can
 * confirm the report. Reporting needs a verified email. Analysis runs synchronously (the form waits).
 */
@RestController
@RequestMapping("/api/reports")
class ReportController {

	private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final ReportDamageUseCase reportDamage;
	private final AnalyzeReportUseCase analyzeReport;
	private final ConfirmReportUseCase confirmReport;
	private final ReporterIds reporterIds;
	private final Clock clock;

	ReportController(ReportDamageUseCase reportDamage, AnalyzeReportUseCase analyzeReport,
			ConfirmReportUseCase confirmReport, ReporterIds reporterIds, Clock clock) {
		this.reportDamage = reportDamage;
		this.analyzeReport = analyzeReport;
		this.confirmReport = confirmReport;
		this.reporterIds = reporterIds;
		this.clock = clock;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<?> create(AuthenticatedUser user,
			@RequestPart("photo") MultipartFile photo,
			@RequestParam double latitude,
			@RequestParam double longitude,
			@RequestParam(required = false) String text) throws IOException {
		requireVerified(user);
		if (photo.isEmpty() || !PHOTO_TYPES.contains(photo.getContentType())) {
			return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PHOTO_REQUIRED", "Envía una foto JPG, PNG o WebP.");
		}
		var submission = new Submission(ReportChannel.WEB, reporterIds.forUser(user.id()),
				new Photo(photo.getBytes(), photo.getContentType()), text, new GeoPoint(latitude, longitude),
				clock.instant());
		var submitted = reportDamage.submit(submission);
		if (submitted.rateLimited()) {
			return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Llegaste al límite de reportes de hoy. Gracias por ayudar; intenta mañana.");
		}
		if (submitted.locationOutsideBogota()) {
			return error(HttpStatus.UNPROCESSABLE_CONTENT, "OUTSIDE_BOGOTA", "Por ahora solo recibimos reportes en Bogotá.");
		}
		return ResponseEntity.ok(AnalysisResponse.from(analyzeReport.analyze(submitted.reportId())));
	}

	@PostMapping("/{id}/confirmation")
	ConfirmationResponse confirm(AuthenticatedUser user, @PathVariable UUID id, @RequestBody ConfirmationRequest request) {
		requireVerified(user);
		return ConfirmationResponse.from(confirmReport.confirm(id, reporterIds.forUser(user.id()), request.accepted()));
	}

	private static void requireVerified(AuthenticatedUser user) {
		if (!user.emailVerified()) {
			throw new AccountException(Code.EMAIL_NOT_VERIFIED, "Confirma tu correo para poder reportar.");
		}
	}

	private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiError(code, message));
	}

	record ConfirmationRequest(boolean accepted) {
	}

	/**
	 * @param outcome AWAITING_CONFIRMATION, PHOTO_REJECTED, MANUAL_REVIEW, or MODELS_UNAVAILABLE
	 */
	record AnalysisResponse(UUID reportId, String outcome, String damageType, String urgency, String summary,
			List<String> locationHints, String agency, String markedPhotoUrl) {

		static AnalysisResponse from(AnalysisResult r) {
			var i = r.interpretation();
			return new AnalysisResponse(r.reportId(),
					r.outcome() == null ? "MODELS_UNAVAILABLE" : r.outcome().name(),
					i == null ? null : i.damageType().name(),
					i == null ? null : i.urgency().name(),
					r.outcome() == AnalysisOutcome.PHOTO_REJECTED || i == null ? null : i.summary(),
					i == null ? List.of() : i.locationHints(),
					r.agencyName(),
					r.markedMediaId() == null ? null : PublicMediaUrls.of(r.markedMediaId()));
		}
	}

	record ConfirmationResponse(UUID reportId, boolean accepted, UUID issueId, boolean newIssue, int reportCount) {

		static ConfirmationResponse from(ConfirmationResult r) {
			return new ConfirmationResponse(r.reportId(), r.accepted(), r.issueId(), r.newIssue(), r.reportCount());
		}
	}
}
