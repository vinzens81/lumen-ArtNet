package de.vinzens.lumen.adapter.web;

import de.vinzens.lumen.adapter.web.access.ConfigLock.LockedException;
import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.domain.DomainValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain errors into HTTP responses (RFC 9457 problem details).
 */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler
	ProblemDetail validationFailed(DomainValidationException e) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid input");
		problem.setProperty("errors", e.errors());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	/** The client has to unlock setup with the PIN; {@code locked: true} tells the UI to ask for it. */
	@ExceptionHandler
	ProblemDetail locked(LockedException e) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
		problem.setProperty("locked", true);
		return problem;
	}

	@ExceptionHandler
	ProblemDetail conflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}
}
