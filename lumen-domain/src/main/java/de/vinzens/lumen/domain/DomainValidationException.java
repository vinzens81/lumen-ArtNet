package de.vinzens.lumen.domain;

import java.util.List;

/**
 * Invalid input for a domain object. Contains all errors found, not just the first one.
 */
public class DomainValidationException extends RuntimeException {

	private final List<String> errors;

	public DomainValidationException(List<String> errors) {
		super(String.join("; ", errors));
		this.errors = List.copyOf(errors);
	}

	public List<String> errors() {
		return errors;
	}
}
