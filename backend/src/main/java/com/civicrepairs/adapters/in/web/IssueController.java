package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.EndorseIssueUseCase;
import com.civicrepairs.application.port.in.EndorseIssueUseCase.EndorsementResult;
import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.PublicIssue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public API for the map. Only blurred media URLs, never reporter data. */
@RestController
@RequestMapping("/api/issues")
class IssueController {

	private final ListPublicIssuesUseCase listPublicIssues;
	private final EndorseIssueUseCase endorseIssue;

	IssueController(ListPublicIssuesUseCase listPublicIssues, EndorseIssueUseCase endorseIssue) {
		this.listPublicIssues = listPublicIssues;
		this.endorseIssue = endorseIssue;
	}

	@GetMapping
	List<IssueResponse> list() {
		return listPublicIssues.listPublicIssues().stream().map(IssueResponse::from).toList();
	}

	/** "I saw it too". Needs a verified account so one person counts once. */
	@PostMapping("/{id}/endorsements")
	EndorsementResponse endorse(@PathVariable UUID id, AuthenticatedUser user) {
		if (!user.emailVerified()) {
			throw new AccountException(Code.EMAIL_NOT_VERIFIED, "Confirma tu correo para continuar.");
		}
		EndorsementResult result = endorseIssue.endorse(id, user.id());
		return new EndorsementResponse(result.issueId(), result.counted(), result.reportCount());
	}

	record EndorsementResponse(UUID issueId, boolean counted, int reportCount) {
	}

	record IssueResponse(UUID id, String damageType, String status, double latitude, double longitude,
			int reportCount, Instant openedAt, String agency, String photoUrl) {

		static IssueResponse from(PublicIssue issue) {
			return new IssueResponse(issue.id(), issue.damageType().name(), issue.status().name(), issue.latitude(),
					issue.longitude(), issue.reportCount(), issue.openedAt(), issue.agencyName(),
					issue.coverMediaId() == null ? null : PublicMediaUrls.of(issue.coverMediaId()));
		}
	}
}
