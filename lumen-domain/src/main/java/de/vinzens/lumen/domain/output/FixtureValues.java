package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Normalized attribute values (0.0–1.0) of one fixture. Attributes that are not set keep their default.
 */
public record FixtureValues(Map<Attribute, Double> values) {

	public static final FixtureValues EMPTY = new FixtureValues(Map.of());

	public FixtureValues {
		var errors = new ArrayList<String>();
		values.forEach((attribute, value) -> {
			if (value == null || value.isNaN() || value < 0 || value > 1) {
				errors.add(attribute.id() + " must be between 0 and 1, was " + value);
			}
		});
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
		values = Map.copyOf(values);
	}

	public Optional<Double> get(Attribute attribute) {
		return Optional.ofNullable(values.get(attribute));
	}

	/** Returns new values in which the given values override the existing ones. */
	public FixtureValues with(FixtureValues overrides) {
		var merged = new HashMap<>(values);
		merged.putAll(overrides.values());
		return new FixtureValues(merged);
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}
}
