package com.civicrepairs.application.port.in;

import java.util.List;
import java.util.UUID;

public interface ListMyReportsUseCase {

	List<MyReport> listMyReports(UUID userId);
}
