package de.vinzens.lumen.adapter.web;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.domain.DomainValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Übersetzt fachliche Fehler in HTTP-Antworten (RFC 9457 Problem Details).
 */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler
	ProblemDetail validationFailed(DomainValidationException e) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Ungültige Eingaben");
		problem.setProperty("errors", e.errors());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail conflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}
}
