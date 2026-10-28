package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AdminOverview;
import com.civicrepairs.application.port.in.AdminUseCase;
import com.civicrepairs.application.port.in.AdminUseCase.AgencyResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Admin panel API. {@link AdminTokenInterceptor} checks ADMIN_TOKEN before any of this runs. */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminUseCase admin;

	AdminController(AdminUseCase admin) {
		this.admin = admin;
	}

	@GetMapping("/overview")
	OverviewResponse overview() {
		return OverviewResponse.from(admin.overview());
	}

	/** Simulates the agency's reply to the petition. */
	@PostMapping("/issues/{id}/agency-responses")
	AgencyResponseResponse agencyResponse(@PathVariable UUID id, @RequestBody AgencyResponseRequest request) {
		AgencyResponseResult r = admin.registerAgencyResponse(id, request.body());
		return new AgencyResponseResponse(r.issueId(), r.classification().name(), r.summary(), r.status().name());
	}

	record AgencyResponseRequest(String body) {
	}

	record AgencyResponseResponse(UUID issueId, String classification, String summary, String status) {
	}

	record ReviewItemResponse(UUID reportId, String channel, Instant createdAt, String damageType, String summary,
			String reason) {
	}

	record OverviewResponse(boolean metaEnabled, BigDecimal spentUsd, BigDecimal budgetUsd,
			Map<String, Integer> issuesByStatus, List<ReviewItemResponse> manualReview) {

		static OverviewResponse from(AdminOverview o) {
			Map<String, Integer> byStatus = new TreeMap<>();
			o.issuesByStatus().forEach((status, n) -> byStatus.put(status.name(), n));
			return new OverviewResponse(o.metaEnabled(), o.spentUsd(), o.budgetUsd(), byStatus,
					o.manualReview().stream().map(r -> new ReviewItemResponse(r.reportId(), r.channel().name(),
							r.createdAt(), r.damageType() == null ? null : r.damageType().name(), r.summary(),
							r.reason())).toList());
		}
	}
}
