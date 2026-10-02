package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a universe.
 *
 * @param number logical number within the project (1, 2, …), shown in the patch
 */
public record UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target) {

	public UniverseSpec {
		Objects.requireNonNull(name, "name");
		new Validation()
				.require(number >= 1, "number must be at least 1, was " + number)
				.require(artNet != null, "artnet is missing")
				.require(target != null, "target is missing")
				.throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
	}
}
