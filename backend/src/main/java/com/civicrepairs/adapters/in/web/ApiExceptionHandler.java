package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
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

	@ExceptionHandler(AccountException.class)
	ResponseEntity<ApiError> account(AccountException e) {
		HttpStatus status = switch (e.code()) {
			case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
			case EMAIL_TAKEN -> HttpStatus.CONFLICT;
			case BAD_CREDENTIALS, UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
			case INVALID_TOKEN -> HttpStatus.UNPROCESSABLE_CONTENT;
			case EMAIL_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
		};
		return ResponseEntity.status(status).body(new ApiError(e.code().name(), e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiError> badRequest() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "No encontramos ese recurso."));
	}

	@ExceptionHandler({InvalidReportStateException.class, IllegalStateException.class})
	ResponseEntity<ApiError> conflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("INVALID_STATE", "Ese reporte ya no se puede modificar."));
	}
}
