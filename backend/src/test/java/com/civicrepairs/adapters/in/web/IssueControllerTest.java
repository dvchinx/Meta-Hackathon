package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.domain.issue.IssueStatus;
import com.civicrepairs.domain.shared.DamageType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IssueControllerTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID COVER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	private MockMvc mvc(PublicIssue... issues) {
		ListPublicIssuesUseCase useCase = () -> List.of(issues);
		return MockMvcBuilders.standaloneSetup(new IssueController(useCase)).build();
	}

	@Test
	void servesTheMapContract() throws Exception {
		var issue = new PublicIssue(ID, DamageType.POTHOLE, IssueStatus.FILED, 4.67, -74.05, 3,
				Instant.parse("2026-10-20T12:00:00Z"), "Instituto de Desarrollo Urbano", COVER);

		mvc(issue).perform(get("/api/issues"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].damageType").value("POTHOLE"))
				.andExpect(jsonPath("$[0].status").value("FILED"))
				.andExpect(jsonPath("$[0].latitude").value(4.67))
				.andExpect(jsonPath("$[0].reportCount").value(3))
				.andExpect(jsonPath("$[0].openedAt").value("2026-10-20T12:00:00Z"))
				.andExpect(jsonPath("$[0].agency").value("Instituto de Desarrollo Urbano"))
				.andExpect(jsonPath("$[0].photoUrl").value("/media/" + COVER + ".jpg"));
	}

	@Test
	void issuesWithoutPhotoOrAgencyStillRender() throws Exception {
		var issue = new PublicIssue(ID, DamageType.OTHER, IssueStatus.REPORTED, 4.6, -74.08, 1,
				Instant.parse("2026-10-20T12:00:00Z"), null, null);

		mvc(issue).perform(get("/api/issues"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].photoUrl").value(nullValue()))
				.andExpect(jsonPath("$[0].agency").value(nullValue()));
	}
}
