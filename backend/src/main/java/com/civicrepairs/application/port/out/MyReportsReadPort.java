package com.civicrepairs.application.port.out;

import com.civicrepairs.application.port.in.MyReport;

import java.util.List;

public interface MyReportsReadPort {

	/** Newest first. Cancelled reports are left out. */
	List<MyReport> findByReporter(String reporterId);
}
