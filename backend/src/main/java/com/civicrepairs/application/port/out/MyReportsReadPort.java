package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.MyReport;

import java.util.List;
import java.util.UUID;

public interface MyReportsReadPort {

	/**
	 * Newest first. Cancelled reports are left out.
	 *
	 * @param userId the same citizen's account, to tell which petitions they filed themselves
	 */
	List<MyReport> findByReporter(String reporterId, UUID userId);
}
