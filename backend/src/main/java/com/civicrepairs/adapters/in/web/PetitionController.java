package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AuthenticatedUser;
import com.civicrepairs.application.port.in.FilePetitionUseCase;
import com.civicrepairs.application.port.in.FilePetitionUseCase.PetitionDocument;
import com.civicrepairs.domain.petition.FilerProfile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The printable petition. Contains the petitioner's personal data: only they can read it, and it is never cached. */
@RestController
@RequestMapping("/api/petitions")
class PetitionController {

	private final FilePetitionUseCase petitions;

	PetitionController(FilePetitionUseCase petitions) {
		this.petitions = petitions;
	}

	@GetMapping("/{id}")
	ResponseEntity<PetitionResponse> get(@PathVariable UUID id, AuthenticatedUser user) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(PetitionResponse.from(petitions.document(id, user.id())));
	}

	record PetitionerResponse(String idType, String idNumber, String fullName, String mobilePhone, String address,
			String email) {
	}

	record PetitionResponse(UUID petitionId, String radicado, Instant filedAt, LocalDate dueDate, String agency,
			PetitionerResponse petitioner, String subject, UUID issueId, String damageType, double latitude,
			double longitude, String locality, int reportCount, Instant openedAt, String photoUrl) {

		static PetitionResponse from(PetitionDocument d) {
			FilerProfile p = d.petitioner().profile();
			return new PetitionResponse(d.petitionId(), d.radicado(), d.filedAt(), d.dueDate(), d.agencyName(),
					new PetitionerResponse(p.idType().name(), p.idNumber(), p.fullName(), p.mobilePhone(), p.address(),
							d.petitioner().email()),
					d.subject(), d.issueId(), d.damageType().name(), d.latitude(), d.longitude(), d.localityName(),
					d.reportCount(), d.openedAt(), d.photoMediaId() == null ? null : PublicMediaUrls.of(d.photoMediaId()));
		}
	}
}
