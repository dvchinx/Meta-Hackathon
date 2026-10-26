package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ListPublicIssuesUseCase;
import com.civicrepairs.application.port.in.PublicIssue;
import com.civicrepairs.application.port.out.PublicIssueReadPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class ListPublicIssuesService implements ListPublicIssuesUseCase {

	private final PublicIssueReadPort issues;

	ListPublicIssuesService(PublicIssueReadPort issues) {
		this.issues = issues;
	}

	@Override
	public List<PublicIssue> listPublicIssues() {
		return issues.findAllPublic();
	}
}
