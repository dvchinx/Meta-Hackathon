package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.IssueActionException;
import com.civicrepairs.application.port.in.IssueActionException.Code;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.application.port.in.VerifyRepairUseCase;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.application.port.out.VerificationRepositoryPort;
import com.civicrepairs.application.port.out.VisionPort;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.issue.RepairCheck;
import com.civicrepairs.domain.issue.RepairVerdict;
import com.civicrepairs.domain.issue.Verification;
import com.civicrepairs.domain.issue.VerificationResult;
import com.civicrepairs.domain.issue.VerificationResult.Outcome;
import com.civicrepairs.domain.media.Media;
import com.civicrepairs.domain.media.MediaKind;
import com.civicrepairs.domain.media.Visibility;
import com.civicrepairs.domain.report.DamageAnalysis;
import com.civicrepairs.domain.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * SAM measures the damage in the new photo; Muse Spark compares it with the original. Both run outside any
 * transaction; the Issue is re-read under lock before the verdict is applied. Muse only ever sees blurred photos.
 */
@Service
class VerifyRepairService implements VerifyRepairUseCase {

	private static final Logger log = LoggerFactory.getLogger(VerifyRepairService.class);

	private final IssueRepositoryPort issues;
	private final ReportRepositoryPort reports;
	private final VerificationRepositoryPort verifications;
	private final MediaStoragePort media;
	private final VisionPort vision;
	private final LanguageModelPort languageModel;
	private final ReportSettings settings;
	private final TransactionTemplate tx;
	private final Clock clock;

	VerifyRepairService(IssueRepositoryPort issues, ReportRepositoryPort reports,
			VerificationRepositoryPort verifications, MediaStoragePort media, VisionPort vision,
			LanguageModelPort languageModel, ReportSettings settings, TransactionTemplate tx, Clock clock) {
		this.issues = issues;
		this.reports = reports;
		this.verifications = verifications;
		this.media = media;
		this.vision = vision;
		this.languageModel = languageModel;
		this.settings = settings;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public VerificationReceipt verify(UUID issueId, String verifierId, Photo photo) {
		Issue issue = issues.findById(issueId).orElseThrow(VerifyRepairService::notFound);
		requireVerifiable(issue);
		Instant now = clock.instant();
		if (verifications.countByVerifierSince(verifierId, now.minus(Duration.ofDays(1)))
				>= settings.reportsPerReporterPerDay()) {
			throw new IssueActionException(Code.RATE_LIMITED,
					"Llegaste al límite de fotos de hoy. Gracias por ayudar; intenta mañana.");
		}
		UUID cover = issue.coverMediaId().orElseThrow(VerifyRepairService::notFound);
		double areaBefore = reports.findFirstConfirmedByIssue(issueId)
				.flatMap(Report::analysis)
				.map(DamageAnalysis::areaFraction)
				.orElse(0.0);

		Media original = media.store(photo.data(), photo.contentType(), MediaKind.PHOTO, Visibility.PRIVATE, null);
		VisionPort.VisionResult after;
		Media blurred;
		RepairVerdict verdict;
		try {
			after = vision.analyze(media.load(original.id()), issue.damageType());
			blurred = media.store(after.blurredJpeg(), "image/jpeg", MediaKind.PHOTO, Visibility.PUBLIC, original.id());
			verdict = languageModel.judgeRepair(media.load(cover), media.load(blurred.id()), issue.damageType());
		} catch (ModelUnavailableException e) {
			log.warn("Verification of Issue {} failed: {} ({})", issueId, e.reason(), e.getMessage());
			throw new IssueActionException(Code.MODELS_UNAVAILABLE,
					"No pudimos analizar la foto en este momento. Intenta de nuevo más tarde.");
		}
		Outcome outcome = RepairCheck.decide(issue.damageType(), areaBefore, after.areaFraction(), verdict);

		IssueStatus status = tx.execute(s -> {
			Issue locked = issues.findByIdForUpdate(issueId).orElseThrow(VerifyRepairService::notFound);
			requireVerifiable(locked);
			locked.applyVerification(new VerificationResult(outcome, blurred.id().toString()), clock.instant());
			issues.save(locked);
			verifications.save(new Verification(UUID.randomUUID(), issueId, verifierId, original.id(), blurred.id(),
					areaBefore, after.areaFraction(), verdict, outcome, clock.instant()));
			return locked.status();
		});
		return new VerificationReceipt(issueId, outcome, status, verdict.reason(), blurred.id());
	}

	private static void requireVerifiable(Issue issue) {
		if (!issue.status().canTransitionTo(IssueStatus.VERIFIED)) {
			throw new IssueActionException(Code.WRONG_STATUS, issue.status() == IssueStatus.VERIFIED
					? "Este daño ya tiene la reparación verificada."
					: "Pedimos la foto de verificación cuando la entidad dice que reparó el daño.");
		}
	}

	private static IssueActionException notFound() {
		return new IssueActionException(Code.NOT_FOUND, "No encontramos ese daño.");
	}
}
