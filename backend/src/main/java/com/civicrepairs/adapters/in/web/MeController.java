package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.ListMyReportsUseCase;
import com.civicrepairs.application.port.in.MyReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The signed-in citizen's own data. */
@RestController
@RequestMapping("/api/me")
class MeController {

	private final ListMyReportsUseCase listMyReports;

	MeController(ListMyReportsUseCase listMyReports) {
		this.listMyReports = listMyReports;
	}

	@GetMapping("/reports")
	List<MyReportResponse> reports(AuthenticatedUser user) {
		return listMyReports.listMyReports(user.id()).stream().map(MyReportResponse::from).toList();
	}

	record MyReportResponse(UUID reportId, String reportStatus, Instant createdAt, String damageType, String summary,
			double latitude, double longitude, UUID issueId, String issueStatus, int issueReportCount, String photoUrl) {

		static MyReportResponse from(MyReport r) {
			return new MyReportResponse(r.reportId(), r.reportStatus().name(), r.createdAt(),
					r.damageType() == null ? null : r.damageType().name(), r.summary(), r.latitude(), r.longitude(),
					r.issueId(), r.issueStatus() == null ? null : r.issueStatus().name(), r.issueReportCount(),
					r.coverMediaId() == null ? null : PublicMediaUrls.of(r.coverMediaId()));
		}
	}
}
