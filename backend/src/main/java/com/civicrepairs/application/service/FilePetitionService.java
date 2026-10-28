package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.FilePetitionUseCase;
import com.civicrepairs.application.port.in.PetitionException;
import com.civicrepairs.application.port.in.PetitionException.Code;
import com.civicrepairs.application.port.in.PrivacyPolicy;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.out.AgencyRepositoryPort;
import com.civicrepairs.application.port.out.FilerProfileRepositoryPort;
import com.civicrepairs.application.port.out.IssueRepositoryPort;
import com.civicrepairs.application.port.out.PetitionRepositoryPort;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import com.civicrepairs.application.port.out.ReportRepositoryPort;
import com.civicrepairs.domain.agency.Agency;
import com.civicrepairs.domain.issue.Issue;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.petition.FilerProfile;
import com.civicrepairs.domain.petition.Petition;
import com.civicrepairs.domain.petition.Petitioner;
import com.civicrepairs.domain.report.Interpretation;
import com.civicrepairs.domain.report.Report;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Any citizen with a verified account can file for an Issue that has no petition in progress. The petitioner's data
 * goes into the petition snapshot; the public only ever sees the radicado and the deadline.
 */
@Service
class FilePetitionService implements FilePetitionUseCase {

	private static final String DEFAULT_SUBJECT = "Daño en el espacio público reportado por ciudadanos.";

	private final IssueRepositoryPort issues;
	private final PetitionRepositoryPort petitions;
	private final FilerProfileRepositoryPort profiles;
	private final ReportRepositoryPort reports;
	private final AgencyRepositoryPort agencies;
	private final PublicIssueReadPort publicIssues;
	private final TransactionTemplate tx;
	private final Clock clock;

	FilePetitionService(IssueRepositoryPort issues, PetitionRepositoryPort petitions,
			FilerProfileRepositoryPort profiles, ReportRepositoryPort reports, AgencyRepositoryPort agencies,
			PublicIssueReadPort publicIssues, TransactionTemplate tx, Clock clock) {
		this.issues = issues;
		this.petitions = petitions;
		this.profiles = profiles;
		this.reports = reports;
		this.agencies = agencies;
		this.publicIssues = publicIssues;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public PetitionReceipt file(UUID issueId, AuthenticatedUser user, String policyVersion) {
		Policies.requireCurrent(policyVersion);
		FilerProfile profile = profiles.find(user.id())
				.orElseThrow(() -> new PetitionException(Code.PROFILE_REQUIRED,
						"Completa tus datos para radicar antes de continuar."));
		String subject = reports.findFirstConfirmedByIssue(issueId)
				.flatMap(Report::interpretation)
				.map(Interpretation::summary)
				.orElse(DEFAULT_SUBJECT);

		Petition petition = tx.execute(status -> {
			Issue issue = issues.findByIdForUpdate(issueId).orElseThrow(FilePetitionService::issueNotFound);
			if (!issue.status().canTransitionTo(IssueStatus.FILED)) {
				throw new PetitionException(Code.ALREADY_FILED,
						"Este daño ya tiene una petición en curso. Puedes seguirla en el mapa.");
			}
			String agencyCode = issue.agencyCode().orElseThrow(() -> new PetitionException(Code.NOT_FILEABLE,
					"Todavía no sabemos qué entidad es responsable de este daño. Nuestro equipo lo revisará."));
			Instant now = clock.instant();
			Petition filed = Petition.file(UUID.randomUUID(), issueId, agencyCode, user.id(), petitions.nextSequence(),
					now, new Petitioner(profile, user.email()), subject, PrivacyPolicy.CURRENT_VERSION);
			petitions.save(filed);
			issue.file(filed.id().toString(), now);
			issues.save(issue);
			return filed;
		});
		return new PetitionReceipt(petition.id(), issueId, petition.radicado(), petition.filedAt(),
				petition.dueDate(), agencyName(petition.agencyCode()));
	}

	@Override
	public PetitionDocument document(UUID petitionId, UUID userId) {
		Petition petition = petitions.findById(petitionId)
				.filter(p -> userId.equals(p.filerUserId()))
				.orElseThrow(() -> new PetitionException(Code.PETITION_NOT_FOUND, "No encontramos esa petición."));
		PublicIssue issue = publicIssues.findPublic(petition.issueId()).orElseThrow(FilePetitionService::issueNotFound);
		return new PetitionDocument(petition.id(), petition.radicado(), petition.filedAt(), petition.dueDate(),
				agencyName(petition.agencyCode()), petition.petitioner(), petition.subject(), issue.id(),
				issue.damageType(), issue.latitude(), issue.longitude(), issue.localityName(), issue.reportCount(),
				issue.openedAt(), issue.coverMediaId());
	}

	private String agencyName(String code) {
		return agencies.findByCode(code).map(Agency::name).orElse(code);
	}

	private static PetitionException issueNotFound() {
		return new PetitionException(Code.ISSUE_NOT_FOUND, "No encontramos ese daño.");
	}
}
