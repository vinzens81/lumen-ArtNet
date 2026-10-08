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
 * @param stageShape    how the fixture is drawn in the stage view
 * @param limits        allowed range of pan and tilt in the show, empty = no limit
 */
public record FixtureSpec(
		String name,
		Optional<String> shortName,
		String fixtureTypeId,
		String modeName,
		long universeId,
		int address,
		Optional<Position> position,
		int sortOrder,
		StageShape stageShape,
		Optional<PositionLimits> limits) {

	public static final int MAX_ADDRESS = 512;

	public FixtureSpec {
		Objects.requireNonNull(shortName, "shortName");
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(stageShape, "stageShape");
		Objects.requireNonNull(limits, "limits");
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

	/** Without limits. */
	public FixtureSpec(String name, Optional<String> shortName, String fixtureTypeId, String modeName, long universeId,
			int address, Optional<Position> position, int sortOrder, StageShape stageShape) {
		this(name, shortName, fixtureTypeId, modeName, universeId, address, position, sortOrder, stageShape,
				Optional.empty());
	}

	/** Without a stage shape: drawn as {@link StageShape#DEFAULT}. */
	public FixtureSpec(String name, Optional<String> shortName, String fixtureTypeId, String modeName, long universeId,
			int address, Optional<Position> position, int sortOrder) {
		this(name, shortName, fixtureTypeId, modeName, universeId, address, position, sortOrder, StageShape.DEFAULT);
	}

	public FixtureSpec withPosition(Optional<Position> newPosition) {
		return new FixtureSpec(name, shortName, fixtureTypeId, modeName, universeId, address, newPosition, sortOrder,
				stageShape, limits);
	}

	public FixtureSpec withStageShape(StageShape newShape) {
		return new FixtureSpec(name, shortName, fixtureTypeId, modeName, universeId, address, position, sortOrder,
				newShape, limits);
	}

	public FixtureSpec withUniverseId(long newUniverseId) {
		return new FixtureSpec(name, shortName, fixtureTypeId, modeName, newUniverseId, address, position, sortOrder,
				stageShape, limits);
	}
}
