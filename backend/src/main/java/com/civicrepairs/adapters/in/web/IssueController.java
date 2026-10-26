package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.PublicIssue;
import org.springframework.web.bind.annotation.GetMapping;
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

	IssueController(ListPublicIssuesUseCase listPublicIssues) {
		this.listPublicIssues = listPublicIssues;
	}

	@GetMapping
	List<IssueResponse> list() {
		return listPublicIssues.listPublicIssues().stream().map(IssueResponse::from).toList();
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
