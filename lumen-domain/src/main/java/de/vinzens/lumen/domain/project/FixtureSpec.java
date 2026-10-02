package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Bearbeitbare Eigenschaften eines gepatchten Geräts.
 *
 * @param shortName     Kurzname für die Bühnenansicht, z.B. „FL“
 * @param fixtureTypeId Referenz in die Gerätebibliothek
 * @param universeId    Universe innerhalb des Projekts
 * @param address       DMX-Startadresse (1–512)
 */
public record FixtureSpec(
		String name,
		Optional<String> shortName,
		String fixtureTypeId,
		String modeName,
		long universeId,
		int address,
		Optional<Position> position,
		int sortOrder) {

	public static final int MAX_ADDRESS = 512;

	public FixtureSpec {
		Objects.requireNonNull(shortName, "shortName");
		Objects.requireNonNull(position, "position");
		new Validation()
				.requireText(name, "name")
				.requireText(fixtureTypeId, "fixtureTypeId")
				.requireText(modeName, "mode")
				.require(universeId > 0, "universeId fehlt")
				.requireRange(address, 1, MAX_ADDRESS, "address")
				.throwIfInvalid();
		name = name.strip();
		shortName = shortName.map(String::strip).filter(s -> !s.isEmpty());
	}
}
