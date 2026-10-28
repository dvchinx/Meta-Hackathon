package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.AdminOverview.ReviewItem;
import com.civicrepairs.domain.issue.IssueStatus;

import java.util.List;
import java.util.Map;

public interface AdminReadPort {

	Map<IssueStatus, Integer> issuesByStatus();

	/** Reports waiting for a person: OTHER damage, invalid model output, models unavailable. Newest first. */
	List<ReviewItem> manualReview(int limit);
}
