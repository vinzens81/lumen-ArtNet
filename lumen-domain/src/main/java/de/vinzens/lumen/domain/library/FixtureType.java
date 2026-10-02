package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Beschreibung eines Gerätemodells aus der Gerätebibliothek, ohne projektspezifische Angaben.
 */
public record FixtureType(
		String id,
		String manufacturer,
		String model,
		FixtureCategory category,
		PhysicalProperties physical,
		List<Mode> modes) {

	public FixtureType {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(manufacturer, "manufacturer");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(physical, "physical");
		modes = List.copyOf(modes);
	}

	public Optional<Mode> mode(String name) {
		return modes.stream().filter(m -> m.name().equals(name)).findFirst();
	}
}
