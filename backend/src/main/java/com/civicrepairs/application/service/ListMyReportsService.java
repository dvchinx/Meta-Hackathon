package com.civicrepairs.application.service;

import com.civicrepairs.application.port.in.ListMyReportsUseCase;
import com.civicrepairs.application.port.in.MyReport;
import com.civicrepairs.application.port.out.MyReportsReadPort;
import com.civicrepairs.domain.report.ReportChannel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class ListMyReportsService implements ListMyReportsUseCase {

	private final MyReportsReadPort reports;
	private final ReporterIds reporterIds;

	ListMyReportsService(MyReportsReadPort reports, ReporterIds reporterIds) {
		this.reports = reports;
		this.reporterIds = reporterIds;
	}

	@Override
	public List<MyReport> listMyReports(UUID userId) {
		return reports.findByReporter(reporterIds.forUser(userId));
	}
}
