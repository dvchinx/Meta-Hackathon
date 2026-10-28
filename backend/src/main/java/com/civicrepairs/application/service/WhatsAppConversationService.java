package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AnalyzeReportUseCase;
import com.civicrepairs.application.port.in.AnalyzeReportUseCase.AnalysisResult;
import com.civicrepairs.application.port.in.ConfirmReportUseCase;
import com.civicrepairs.application.port.in.HandleIncomingMessageUseCase;
import com.civicrepairs.application.port.in.ReportDamageUseCase;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Submission;
import com.civicrepairs.application.port.out.MediaStoragePort;
import com.civicrepairs.application.port.out.MessagingPort;
import com.civicrepairs.application.port.out.ProcessedMessagePort;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.report.Report;
import com.civicrepairs.domain.report.ReportChannel;
import com.civicrepairs.domain.report.ReportStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;

/**
 * The WhatsApp bot: accepts data in any order, asks only for what is missing, sends the marked photo for
 * confirmation and turns a "Sí" into an Issue. Messages of one sender arrive here one at a time.
 */
@Service
class WhatsAppConversationService implements HandleIncomingMessageUseCase {

	private static final Set<String> YES = Set.of("si", "s", "ok", "dale", "correcto", "claro", "listo", "yes");
	private static final Set<String> NO = Set.of("no", "n", "incorrecto", "cancelar", "cancela");

	private final ReportRepositoryPort reports;
	private final ReportDamageUseCase reportDamage;
	private final AnalyzeReportUseCase analyzeReport;
	private final ConfirmReportUseCase confirmReport;
	private final MessagingPort messaging;
	private final MediaStoragePort media;
	private final ReporterIds reporterIds;
	private final ProcessedMessagePort processedMessages;
	private final String publicBaseUrl;

	WhatsAppConversationService(ReportRepositoryPort reports, ReportDamageUseCase reportDamage,
			AnalyzeReportUseCase analyzeReport, ConfirmReportUseCase confirmReport, MessagingPort messaging,
			MediaStoragePort media, ReporterIds reporterIds, ProcessedMessagePort processedMessages,
			@Value("${civic.public-base-url}") String publicBaseUrl) {
		this.reports = reports;
		this.reportDamage = reportDamage;
		this.analyzeReport = analyzeReport;
		this.confirmReport = confirmReport;
		this.messaging = messaging;
		this.media = media;
		this.reporterIds = reporterIds;
		this.processedMessages = processedMessages;
		this.publicBaseUrl = publicBaseUrl;
	}

	@Override
	public void handle(IncomingMessage message) {
		if (!processedMessages.markIfNew(ReportChannel.WHATSAPP.name(), message.messageId())) {
			return;
		}
		try {
			converse(message);
		} catch (RuntimeException e) {
			messaging.sendText(message.from(), BotMessages.UNEXPECTED_ERROR);
			throw e;
		}
	}

	private void converse(IncomingMessage message) {
		String to = message.from();
		String reporterId = reporterIds.hash(ReportChannel.WHATSAPP, to);
		Optional<Report> open = reports.findOpenByReporter(reporterId, ReportChannel.WHATSAPP);

		if (open.map(Report::status).orElse(null) == ReportStatus.AWAITING_CONFIRMATION) {
			answerConfirmation(message, reporterId, open.get());
			return;
		}
		if (open.map(Report::status).orElse(null) == ReportStatus.ANALYZING) {
			messaging.sendText(to, BotMessages.STILL_ANALYZING);
			return;
		}
		if (message.kind() == Kind.UNSUPPORTED || message.kind() == Kind.CONFIRMATION) {
			messaging.sendText(to, BotMessages.UNSUPPORTED);
			return;
		}

		Photo photo = null;
		if (message.kind() == Kind.IMAGE) {
			var downloaded = messaging.downloadMedia(message.mediaId());
			photo = new Photo(downloaded.data(), downloaded.contentType());
		}
		var result = reportDamage.submit(new Submission(ReportChannel.WHATSAPP, reporterId, photo, message.text(),
				message.location(), message.at()));

		if (result.rateLimited()) {
			messaging.sendText(to, BotMessages.RATE_LIMITED);
		} else if (result.locationOutsideBogota()) {
			messaging.sendText(to, BotMessages.OUTSIDE_BOGOTA);
		} else if (result.readyForAnalysis()) {
			messaging.sendText(to, BotMessages.ANALYZING);
			sendAnalysis(to, analyzeReport.analyze(result.reportId()));
		} else {
			messaging.sendText(to, BotMessages.askFor(result.missing(), open.isEmpty()));
		}
	}

	private void sendAnalysis(String to, AnalysisResult analysis) {
		if (analysis.outcome() == null) {
			messaging.sendText(to, BotMessages.MODELS_UNAVAILABLE);
			return;
		}
		switch (analysis.outcome()) {
			case AWAITING_CONFIRMATION -> {
				if (analysis.interpretation().requiresEmergencyCall()) {
					messaging.sendText(to, BotMessages.CALL_123);
				}
				byte[] marked = media.load(analysis.markedMediaId()).data();
				messaging.sendImage(to, marked, BotMessages.summary(analysis.interpretation(), analysis.agencyName()));
				messaging.sendConfirmationRequest(to, BotMessages.CONFIRM_QUESTION);
			}
			case PHOTO_REJECTED -> messaging.sendText(to, BotMessages.PHOTO_REJECTED);
			case MANUAL_REVIEW -> messaging.sendText(to, BotMessages.MANUAL_REVIEW);
		}
	}

	private void answerConfirmation(IncomingMessage message, String reporterId, Report report) {
		Boolean accepted = message.kind() == Kind.CONFIRMATION ? message.confirmation() : parseYesNo(message.text());
		if (accepted == null) {
			messaging.sendConfirmationRequest(message.from(), BotMessages.ANSWER_YES_OR_NO);
			return;
		}
		var result = confirmReport.confirm(report.id(), reporterId, accepted);
		messaging.sendText(message.from(), accepted
				? BotMessages.confirmed(result.newIssue(), result.reportCount(), publicBaseUrl,
						publicBaseUrl + "/#/radicar?issue=" + result.issueId())
				: BotMessages.CANCELLED);
	}

	static Boolean parseYesNo(String text) {
		if (text == null) {
			return null;
		}
		String normalized = Normalizer.normalize(text.strip().toLowerCase(), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.replaceAll("[^a-z ]", "")
				.strip();
		String firstWord = normalized.split(" ")[0];
		if (YES.contains(normalized) || YES.contains(firstWord)) {
			return true;
		}
		if (NO.contains(normalized) || NO.contains(firstWord)) {
			return false;
		}
		return null;
	}
}
