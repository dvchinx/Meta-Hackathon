package com.civicrepairs.domain.issue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IssueStatusTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			REPORTED            | FILED
			FILED               | AGENCY_RESPONDED
			AGENCY_RESPONDED    | REPAIR_CLAIMED
			REPAIR_CLAIMED      | VERIFIED, REOPENED, NEEDS_MORE_EVIDENCE
			NEEDS_MORE_EVIDENCE | VERIFIED, REOPENED, NEEDS_MORE_EVIDENCE
			REOPENED            | FILED
			VERIFIED            |
			""")
	void allowsExactlyTheDocumentedTransitions(IssueStatus from, String allowed) {
		Set<IssueStatus> expected = EnumSet.noneOf(IssueStatus.class);
		if (allowed != null) {
			for (String name : allowed.split(",")) {
				expected.add(IssueStatus.valueOf(name.trim()));
			}
		}

		Set<IssueStatus> actual = EnumSet.noneOf(IssueStatus.class);
		for (IssueStatus to : IssueStatus.values()) {
			if (from.canTransitionTo(to)) {
				actual.add(to);
			}
		}

		assertThat(actual).isEqualTo(expected);
	}
}
