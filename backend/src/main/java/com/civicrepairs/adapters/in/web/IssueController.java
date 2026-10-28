package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.EndorseIssueUseCase;
import com.civicrepairs.application.port.in.EndorseIssueUseCase.EndorsementResult;
import com.civicrepairs.application.port.in.FilePetitionUseCase;
import com.civicrepairs.application.port.in.FilePetitionUseCase.PetitionReceipt;
import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.OfficialChannel;
import com.civicrepairs.application.port.in.PetitionException;
import com.civicrepairs.application.port.in.PublicIssue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public API for the map. Only blurred media URLs, never reporter or petitioner data. */
@RestController
@RequestMapping("/api/issues")
class IssueController {

	private final ListPublicIssuesUseCase listPublicIssues;
	private final EndorseIssueUseCase endorseIssue;
	private final FilePetitionUseCase filePetition;
	private final Clock clock;

	IssueController(ListPublicIssuesUseCase listPublicIssues, EndorseIssueUseCase endorseIssue,
			FilePetitionUseCase filePetition, Clock clock) {
		this.listPublicIssues = listPublicIssues;
		this.endorseIssue = endorseIssue;
		this.filePetition = filePetition;
		this.clock = clock;
	}

	@GetMapping
	List<IssueResponse> list() {
		return listPublicIssues.listPublicIssues().stream().map(issue -> IssueResponse.from(issue, clock)).toList();
	}

	@GetMapping("/{id}")
	IssueResponse get(@PathVariable UUID id) {
		return listPublicIssues.findPublicIssue(id)
				.map(issue -> IssueResponse.from(issue, clock))
				.orElseThrow(() -> new PetitionException(PetitionException.Code.ISSUE_NOT_FOUND, "No encontramos ese daño."));
	}

	/** "I saw it too". Needs a verified account so one person counts once. */
	@PostMapping("/{id}/endorsements")
	EndorsementResponse endorse(@PathVariable UUID id, AuthenticatedUser user) {
		requireVerified(user);
		EndorsementResult result = endorseIssue.endorse(id, user.id());
		return new EndorsementResponse(result.issueId(), result.counted(), result.reportCount());
	}

	/** Simulated filing with the citizen's own data. Needs their consent to share it with the agency. */
	@PostMapping("/{id}/petitions")
	PetitionReceiptResponse file(@PathVariable UUID id, AuthenticatedUser user, @RequestBody FileRequest request) {
		requireVerified(user);
		return PetitionReceiptResponse.from(filePetition.file(id, user, request.acceptedPolicyVersion()));
	}

	private static void requireVerified(AuthenticatedUser user) {
		if (!user.emailVerified()) {
			throw new AccountException(Code.EMAIL_NOT_VERIFIED, "Confirma tu correo para continuar.");
		}
	}

	record EndorsementResponse(UUID issueId, boolean counted, int reportCount) {
	}

	/** @param acceptedPolicyVersion the data policy version shown when the citizen authorized sharing their data */
	record FileRequest(String acceptedPolicyVersion) {
	}

	record PetitionReceiptResponse(UUID petitionId, UUID issueId, String radicado, Instant filedAt, LocalDate dueDate,
			String agency) {

		static PetitionReceiptResponse from(PetitionReceipt r) {
			return new PetitionReceiptResponse(r.petitionId(), r.issueId(), r.radicado(), r.filedAt(), r.dueDate(),
					r.agencyName());
		}
	}

	record IssueResponse(UUID id, String damageType, String status, double latitude, double longitude,
			int reportCount, Instant openedAt, String agency, String photoUrl, String locality,
			OfficialChannel officialChannel, PetitionStatusResponse petition) {

		static IssueResponse from(PublicIssue issue, Clock clock) {
			return new IssueResponse(issue.id(), issue.damageType().name(), issue.status().name(), issue.latitude(),
					issue.longitude(), issue.reportCount(), issue.openedAt(), issue.agencyName(),
					issue.coverMediaId() == null ? null : PublicMediaUrls.of(issue.coverMediaId()),
					issue.localityName(), issue.channel(), PetitionStatusResponse.from(issue.petition(), clock));
		}
	}
}
