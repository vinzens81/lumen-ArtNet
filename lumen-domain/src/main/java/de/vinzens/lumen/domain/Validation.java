package de.vinzens.lumen.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Sammelt Validierungsfehler und wirft am Ende eine {@link DomainValidationException}.
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
		return require(value != null && !value.isBlank(), field + " fehlt");
	}

	public Validation requireRange(int value, int min, int max, String field) {
		return require(value >= min && value <= max,
				field + " muss zwischen " + min + " und " + max + " liegen, ist " + value);
	}

	public void throwIfInvalid() {
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
	}
}
