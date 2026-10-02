package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a patched fixture.
 *
 * @param shortName     short name for the stage view, e.g. "FL"
 * @param fixtureTypeId reference into the fixture library
 * @param universeId    universe within the project
 * @param address       DMX start address (1–512)
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
				.require(universeId > 0, "universeId is missing")
				.requireRange(address, 1, MAX_ADDRESS, "address")
				.throwIfInvalid();
		name = name.strip();
		shortName = shortName.map(String::strip).filter(s -> !s.isEmpty());
	}
}
