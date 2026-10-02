package de.vinzens.lumen.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects validation errors and finally throws a {@link DomainValidationException}.
 */
public final class Validation {

	private final List<String> errors = new ArrayList<>();

	public Validation require(boolean condition, String message) {
		if (!condition) {
			errors.add(message);
		}
		return this;
	}

	public Validation requireText(String value, String field) {
		return require(value != null && !value.isBlank(), field + " is missing");
	}

	public Validation requireRange(int value, int min, int max, String field) {
		return require(value >= min && value <= max,
				field + " must be between " + min + " and " + max + ", was " + value);
	}

	public void throwIfInvalid() {
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
	}
}
