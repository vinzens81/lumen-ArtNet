package de.vinzens.lumen.domain;

import java.util.List;

/**
 * Ungültige Eingaben für ein Domain-Objekt. Enthält alle gefundenen Fehler, nicht nur den ersten.
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
