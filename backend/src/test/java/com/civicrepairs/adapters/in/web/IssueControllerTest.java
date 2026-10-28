package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.FiledPetition;
import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.OfficialChannel;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.service.ReporterIds;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IssueControllerTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID COVER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
	// Monday 2026-11-09, 10:00 in Bogotá.
	private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);

	private MockMvc mvc(PublicIssue... issues) {
		ListPublicIssuesUseCase useCase = new ListPublicIssuesUseCase() {
			@Override
			public List<PublicIssue> listPublicIssues() {
				return List.of(issues);
			}

			@Override
			public Optional<PublicIssue> findPublicIssue(UUID id) {
				return List.of(issues).stream().filter(i -> i.id().equals(id)).findFirst();
			}
		};
		var controller = new IssueController(useCase, (issueId, userId) -> {
			throw new UnsupportedOperationException();
		}, null, null, null, null, new ReporterIds("test-salt-at-least-16"), NOW);
		return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	void servesTheMapContract() throws Exception {
		var petition = new FiledPetition("SIM-2026-000007", Instant.parse("2026-10-27T15:00:00Z"),
				LocalDate.of(2026, 11, 19));
		var issue = new PublicIssue(ID, DamageType.POTHOLE, IssueStatus.FILED, 4.67, -74.05, 3,
				Instant.parse("2026-10-20T12:00:00Z"), "Unidad de Mantenimiento Vial", COVER, "Chapinero",
				new OfficialChannel("https://bogota.gov.co/yo-participo/reporta-sdqs/huecos", "Línea 195"), petition);

		mvc(issue).perform(get("/api/issues"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].damageType").value("POTHOLE"))
				.andExpect(jsonPath("$[0].status").value("FILED"))
				.andExpect(jsonPath("$[0].latitude").value(4.67))
				.andExpect(jsonPath("$[0].reportCount").value(3))
				.andExpect(jsonPath("$[0].openedAt").value("2026-10-20T12:00:00Z"))
				.andExpect(jsonPath("$[0].agency").value("Unidad de Mantenimiento Vial"))
				.andExpect(jsonPath("$[0].locality").value("Chapinero"))
				.andExpect(jsonPath("$[0].officialChannel.phone").value("Línea 195"))
				.andExpect(jsonPath("$[0].petition.radicado").value("SIM-2026-000007"))
				.andExpect(jsonPath("$[0].petition.dueDate").value("2026-11-19"))
				// Nov 10–13, (16 is a holiday), 17, 18, 19.
				.andExpect(jsonPath("$[0].petition.businessDaysLeft").value(7))
				.andExpect(jsonPath("$[0].petition.overdue").value(false))
				.andExpect(jsonPath("$[0].photoUrl").value("/media/" + COVER + ".jpg"));
	}

	@Test
	void issuesWithoutPhotoAgencyOrPetitionStillRender() throws Exception {
		var issue = new PublicIssue(ID, DamageType.OTHER, IssueStatus.REPORTED, 4.6, -74.08, 1,
				Instant.parse("2026-10-20T12:00:00Z"), null, null, null, null, null);

		mvc(issue).perform(get("/api/issues"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].photoUrl").value(nullValue()))
				.andExpect(jsonPath("$[0].agency").value(nullValue()))
				.andExpect(jsonPath("$[0].petition").value(nullValue()));
	}

	@Test
	void servesOneIssue() throws Exception {
		var issue = new PublicIssue(ID, DamageType.POTHOLE, IssueStatus.REPORTED, 4.6, -74.08, 1,
				Instant.parse("2026-10-20T12:00:00Z"), null, null, null, null, null);

		mvc(issue).perform(get("/api/issues/" + ID)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ID.toString()));
		mvc(issue).perform(get("/api/issues/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}
}
