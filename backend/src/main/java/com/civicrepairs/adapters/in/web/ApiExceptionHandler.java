package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.ReportNotFoundException;
import com.civicrepairs.domain.report.InvalidReportStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ReportNotFoundException.class)
	ResponseEntity<ApiError> notFound() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiError("REPORT_NOT_FOUND", "No encontramos ese reporte."));
	}

	@ExceptionHandler({InvalidReportStateException.class, IllegalStateException.class})
	ResponseEntity<ApiError> conflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("INVALID_STATE", "Ese reporte ya no se puede modificar."));
	}
}
