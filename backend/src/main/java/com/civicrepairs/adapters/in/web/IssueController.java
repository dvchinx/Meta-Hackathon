package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.AccountException.Code;
import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.EndorseIssueUseCase;
import com.civicrepairs.application.port.in.EndorseIssueUseCase.EndorsementResult;
import com.civicrepairs.application.port.in.ExampleNote;
import com.civicrepairs.application.port.in.FilePetitionUseCase;
import com.civicrepairs.application.port.in.FilePetitionUseCase.PetitionReceipt;
import com.civicrepairs.application.port.in.FollowIssueUseCase;
import com.civicrepairs.application.port.in.IssueActionException;
import com.civicrepairs.application.port.in.IssueHistory;
import com.civicrepairs.application.port.in.IssueHistoryUseCase;
import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.OfficialChannel;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.in.ReportDamageUseCase.Photo;
import com.civicrepairs.application.port.in.VerifyRepairUseCase;
import com.civicrepairs.application.port.in.VerifyRepairUseCase.VerificationReceipt;
import com.civicrepairs.application.service.ReporterIds;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API for the map. Only blurred media URLs, never reporter or petitioner data. Whoever endorses, files or
 * verifies an Issue starts following it (notices by email).
 */
@RestController
@RequestMapping("/api/issues")
class IssueController {

	private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final ListPublicIssuesUseCase listPublicIssues;
	private final EndorseIssueUseCase endorseIssue;
	private final FilePetitionUseCase filePetition;
	private final IssueHistoryUseCase issueHistory;
	private final VerifyRepairUseCase verifyRepair;
	private final FollowIssueUseCase follow;
	private final ReporterIds reporterIds;
	private final Clock clock;

	IssueController(ListPublicIssuesUseCase listPublicIssues, EndorseIssueUseCase endorseIssue,
			FilePetitionUseCase filePetition, IssueHistoryUseCase issueHistory, VerifyRepairUseCase verifyRepair,
			FollowIssueUseCase follow, ReporterIds reporterIds, Clock clock) {
		this.listPublicIssues = listPublicIssues;
		this.endorseIssue = endorseIssue;
		this.filePetition = filePetition;
		this.issueHistory = issueHistory;
		this.verifyRepair = verifyRepair;
		this.follow = follow;
		this.reporterIds = reporterIds;
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
				.orElseThrow(() -> new IssueActionException(IssueActionException.Code.NOT_FOUND, "No encontramos ese daño."));
	}

	/** Public timeline: status changes, agency replies and before/after checks. */
	@GetMapping("/{id}/history")
	HistoryResponse history(@PathVariable UUID id) {
		get(id);
		return HistoryResponse.from(issueHistory.history(id));
	}

	/** "I saw it too". Needs a verified account so one person counts once. */
	@PostMapping("/{id}/endorsements")
	EndorsementResponse endorse(@PathVariable UUID id, AuthenticatedUser user) {
		requireVerified(user);
		EndorsementResult result = endorseIssue.endorse(id, user.id());
		follow.followWithAccount(id, user.id());
		return new EndorsementResponse(result.issueId(), result.counted(), result.reportCount());
	}

	/** Simulated filing with the citizen's own data. Needs their consent to share it with the agency. */
	@PostMapping("/{id}/petitions")
	PetitionReceiptResponse file(@PathVariable UUID id, AuthenticatedUser user, @RequestBody FileRequest request) {
		requireVerified(user);
		PetitionReceipt receipt = filePetition.file(id, user, request.acceptedPolicyVersion());
		follow.followWithAccount(id, user.id());
		return PetitionReceiptResponse.from(receipt);
	}

	/** Before/after check with a new photo of the place. The analysis runs while the request waits. */
	@PostMapping(path = "/{id}/verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VerificationResponse verify(@PathVariable UUID id, AuthenticatedUser user,
			@RequestPart("photo") MultipartFile photo) throws IOException {
		requireVerified(user);
		if (photo.isEmpty() || !PHOTO_TYPES.contains(photo.getContentType())) {
			throw new IssueActionException(IssueActionException.Code.INVALID_INPUT, "Envía una foto JPG, PNG o WebP.");
		}
		VerificationReceipt receipt = verifyRepair.verify(id, reporterIds.forUser(user.id()),
				new Photo(photo.getBytes(), photo.getContentType()));
		follow.followWithAccount(id, user.id());
		return new VerificationResponse(receipt.issueId(), receipt.outcome().name(), receipt.status().name(),
				receipt.reason(), PublicMediaUrls.of(receipt.photoMediaId()));
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

	/** @param outcome REPAIRED, NOT_REPAIRED or INCONCLUSIVE; {@code status} is the Issue's new status */
	record VerificationResponse(UUID issueId, String outcome, String status, String reason, String photoUrl) {
	}

	record StepResponse(String from, String to, String cause, Instant at) {
	}

	record AgencyResponseResponse(Instant at, String classification, String summary, String body) {
	}

	record CheckResponse(Instant at, String outcome, String reason, String photoUrl) {
	}

	record HistoryResponse(List<StepResponse> steps, List<AgencyResponseResponse> agencyResponses,
			List<CheckResponse> verifications) {

		static HistoryResponse from(IssueHistory h) {
			return new HistoryResponse(
					h.steps().stream().map(s -> new StepResponse(s.from() == null ? null : s.from().name(),
							s.to().name(), s.cause().name(), s.at())).toList(),
					h.agencyResponses().stream().map(r -> new AgencyResponseResponse(r.at(), r.classification().name(),
							r.summary(), r.body())).toList(),
					h.verifications().stream().map(c -> new CheckResponse(c.at(), c.outcome().name(), c.reason(),
							PublicMediaUrls.of(c.photoMediaId()))).toList());
		}
	}

	record IssueResponse(UUID id, String damageType, String status, double latitude, double longitude,
			int reportCount, Instant openedAt, String agency, String photoUrl, String locality,
			OfficialChannel officialChannel, PetitionStatusResponse petition, ExampleNote example) {

		static IssueResponse from(PublicIssue issue, Clock clock) {
			return new IssueResponse(issue.id(), issue.damageType().name(), issue.status().name(), issue.latitude(),
					issue.longitude(), issue.reportCount(), issue.openedAt(), issue.agencyName(),
					issue.coverMediaId() == null ? null : PublicMediaUrls.of(issue.coverMediaId()),
					issue.localityName(), issue.channel(), PetitionStatusResponse.from(issue.petition(), clock),
					issue.example());
		}
	}
}
