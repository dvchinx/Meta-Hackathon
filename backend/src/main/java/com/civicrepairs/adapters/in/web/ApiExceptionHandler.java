package com.civicrepairs.adapters.in.web;

import com.civicrepairs.application.port.in.AccountException;
import com.civicrepairs.application.port.in.IssueActionException;
import com.civicrepairs.application.port.in.PetitionException;
import com.civicrepairs.application.port.in.ReportNotFoundException;
import com.civicrepairs.application.port.out.PersonalDataUnavailableException;
import com.civicrepairs.domain.report.InvalidReportStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

	@ExceptionHandler(PetitionException.class)
	ResponseEntity<ApiError> petition(PetitionException e) {
		HttpStatus status = switch (e.code()) {
			case INVALID_PROFILE -> HttpStatus.BAD_REQUEST;
			case ISSUE_NOT_FOUND, PETITION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case PROFILE_REQUIRED, POLICY_OUTDATED, ALREADY_FILED, NOT_FILEABLE -> HttpStatus.CONFLICT;
		};
		return ResponseEntity.status(status).body(new ApiError(e.code().name(), e.getMessage()));
	}

	@ExceptionHandler(IssueActionException.class)
	ResponseEntity<ApiError> issueAction(IssueActionException e) {
		HttpStatus status = switch (e.code()) {
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case WRONG_STATUS -> HttpStatus.CONFLICT;
			case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
			case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
			case MODELS_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
		return ResponseEntity.status(status).body(new ApiError(e.code().name(), e.getMessage()));
	}

	@ExceptionHandler(PersonalDataUnavailableException.class)
	ResponseEntity<ApiError> personalDataUnavailable(PersonalDataUnavailableException e) {
		log.error("Personal data unavailable: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("FILING_UNAVAILABLE",
				"La radicación no está disponible en este momento. Intenta más tarde."));
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
