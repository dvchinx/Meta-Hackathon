package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AdminOverview;
import com.civicrepairs.application.port.in.AdminUseCase;
import com.civicrepairs.application.port.in.IssueActionException;
import com.civicrepairs.application.port.in.IssueActionException.Code;
import com.civicrepairs.application.port.out.AdminReadPort;
import com.civicrepairs.application.port.out.AgencyResponseRepositoryPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.application.port.out.LanguageModelPort;
import com.civicrepairs.application.port.out.ModelCallLedgerPort;
import com.civicrepairs.application.port.out.ModelUnavailableException;
import com.civicrepairs.domain.issue.AgencyResponse;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.IssueStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** The model call runs before the lock; the Issue is re-read and re-checked inside the transaction. */
@Service
class AdminService implements AdminUseCase {

	private static final int REVIEW_LIMIT = 50;

	private final AdminReadPort admin;
	private final ModelCallLedgerPort ledger;
	private final IssueRepositoryPort issues;
	private final AgencyResponseRepositoryPort responses;
	private final LanguageModelPort languageModel;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final boolean metaEnabled;
	private final BigDecimal budgetUsd;

	AdminService(AdminReadPort admin, ModelCallLedgerPort ledger, IssueRepositoryPort issues,
			AgencyResponseRepositoryPort responses, LanguageModelPort languageModel, TransactionTemplate tx,
			Clock clock, @Value("${civic.meta.enabled}") boolean metaEnabled,
			@Value("${civic.meta.budget-limit-usd}") BigDecimal budgetUsd) {
		this.admin = admin;
		this.ledger = ledger;
		this.issues = issues;
		this.responses = responses;
		this.languageModel = languageModel;
		this.tx = tx;
		this.clock = clock;
		this.metaEnabled = metaEnabled;
		this.budgetUsd = budgetUsd;
	}

	@Override
	public AdminOverview overview() {
		return new AdminOverview(metaEnabled, ledger.totalSpentUsd(), budgetUsd, admin.issuesByStatus(),
				admin.manualReview(REVIEW_LIMIT));
	}

	@Override
	public AgencyResponseResult registerAgencyResponse(UUID issueId, String body) {
		String text = body == null ? "" : body.strip();
		if (text.isEmpty() || text.length() > AgencyResponse.MAX_BODY) {
			throw new IssueActionException(Code.INVALID_INPUT,
					"Escribe la respuesta de la entidad (hasta " + AgencyResponse.MAX_BODY + " caracteres).");
		}
		requireFiled(issues.findById(issueId).orElseThrow(AdminService::notFound));

		AgencyResponse.Reading reading;
		try {
			reading = languageModel.classifyAgencyResponse(text);
		} catch (ModelUnavailableException e) {
			throw new IssueActionException(Code.MODELS_UNAVAILABLE,
					"No pudimos leer la respuesta con el modelo. Intenta de nuevo en un momento.");
		}

		return tx.execute(status -> {
			Issue issue = issues.findByIdForUpdate(issueId).orElseThrow(AdminService::notFound);
			requireFiled(issue);
			Instant now = clock.instant();
			var response = new AgencyResponse(UUID.randomUUID(), issueId, text, reading.classification(),
					reading.summary(), now);
			responses.save(response);
			issue.registerAgencyResponse(reading.classification(), response.id().toString(), now);
			issues.save(issue);
			return new AgencyResponseResult(issueId, reading.classification(), reading.summary(), issue.status());
		});
	}

	private static void requireFiled(Issue issue) {
		if (!issue.status().canTransitionTo(IssueStatus.AGENCY_RESPONDED)) {
			throw new IssueActionException(Code.WRONG_STATUS,
					"Solo se registra la respuesta de un daño con la petición radicada y sin responder.");
		}
	}

	private static IssueActionException notFound() {
		return new IssueActionException(Code.NOT_FOUND, "No encontramos ese daño.");
	}
}
