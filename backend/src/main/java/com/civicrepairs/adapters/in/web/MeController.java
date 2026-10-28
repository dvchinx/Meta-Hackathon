package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.ListMyReportsUseCase;
import com.civicrepairs.application.port.in.ManageFilerProfileUseCase;
import com.civicrepairs.application.port.in.ManageFilerProfileUseCase.ProfileInput;
import com.civicrepairs.application.port.in.MyReport;
import com.civicrepairs.domain.petition.FilerProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The signed-in citizen's own data. */
@RestController
@RequestMapping("/api/me")
class MeController {

	private final ListMyReportsUseCase listMyReports;
	private final ManageFilerProfileUseCase filerProfiles;
	private final Clock clock;

	MeController(ListMyReportsUseCase listMyReports, ManageFilerProfileUseCase filerProfiles, Clock clock) {
		this.listMyReports = listMyReports;
		this.filerProfiles = filerProfiles;
		this.clock = clock;
	}

	@GetMapping("/reports")
	List<MyReportResponse> reports(AuthenticatedUser user) {
		return listMyReports.listMyReports(user.id()).stream().map(r -> MyReportResponse.from(r, clock)).toList();
	}

	/** The data for filing petitions. {@code profile} is null when the citizen never gave it (or deleted it). */
	@GetMapping("/filer-profile")
	FilerProfileResponse filerProfile(AuthenticatedUser user) {
		return new FilerProfileResponse(filerProfiles.find(user.id()).map(ProfileBody::from).orElse(null));
	}

	@PutMapping("/filer-profile")
	FilerProfileResponse saveFilerProfile(AuthenticatedUser user, @RequestBody SaveProfileRequest request) {
		ProfileBody p = request.profile() == null ? ProfileBody.EMPTY : request.profile();
		ProfileInput input = new ProfileInput(p.idType(), p.idNumber(), p.firstNames(), p.lastNames(),
				p.mobilePhone(), p.address());
		return new FilerProfileResponse(
				ProfileBody.from(filerProfiles.save(user.id(), input, request.acceptedPolicyVersion())));
	}

	@DeleteMapping("/filer-profile")
	ResponseEntity<Void> deleteFilerProfile(AuthenticatedUser user) {
		filerProfiles.delete(user.id());
		return ResponseEntity.noContent().build();
	}

	record ProfileBody(String idType, String idNumber, String firstNames, String lastNames, String mobilePhone,
			String address) {

		static final ProfileBody EMPTY = new ProfileBody(null, null, null, null, null, null);

		static ProfileBody from(FilerProfile p) {
			return new ProfileBody(p.idType().name(), p.idNumber(), p.firstNames(), p.lastNames(), p.mobilePhone(),
					p.address());
		}
	}

	record FilerProfileResponse(ProfileBody profile) {
	}

	record SaveProfileRequest(ProfileBody profile, String acceptedPolicyVersion) {
	}

	record MyReportResponse(UUID reportId, String reportStatus, Instant createdAt, String damageType, String summary,
			double latitude, double longitude, UUID issueId, String issueStatus, int issueReportCount, String photoUrl,
			String agency, String locality, PetitionStatusResponse petition, UUID myPetitionId) {

		static MyReportResponse from(MyReport r, Clock clock) {
			return new MyReportResponse(r.reportId(), r.reportStatus().name(), r.createdAt(),
					r.damageType() == null ? null : r.damageType().name(), r.summary(), r.latitude(), r.longitude(),
					r.issueId(), r.issueStatus() == null ? null : r.issueStatus().name(), r.issueReportCount(),
					r.coverMediaId() == null ? null : PublicMediaUrls.of(r.coverMediaId()), r.agencyName(),
					r.localityName(), PetitionStatusResponse.from(r.petition(), clock), r.myPetitionId());
		}
	}
}
