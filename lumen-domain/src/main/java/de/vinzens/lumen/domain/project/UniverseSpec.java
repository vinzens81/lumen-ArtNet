package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Bearbeitbare Eigenschaften eines Universe.
 *
 * @param number logische Nummer im Projekt (1, 2, …), wird im Patch angezeigt
 */
public record UniverseSpec(int number, Optional<String> name, ArtNetAddress artNet, ArtNetTarget target) {

	public UniverseSpec {
		Objects.requireNonNull(name, "name");
		new Validation()
				.require(number >= 1, "number muss mindestens 1 sein, ist " + number)
				.require(artNet != null, "artnet fehlt")
				.require(target != null, "target fehlt")
				.throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
	}
}
