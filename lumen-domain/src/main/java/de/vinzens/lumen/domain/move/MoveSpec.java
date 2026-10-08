package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Editable properties of a move: a figure laid onto fixtures.
 *
 * @param fixtures        in order; the order decides the offset
 * @param sizeX           scale of pan around the middle of the figure (1 = 100 %)
 * @param sizeY           scale of tilt
 * @param shiftX          shift of the whole figure in pan, normalized (−1 … 1 = −255 … +255 DMX)
 * @param shiftY          shift in tilt
 * @param rotationDegrees rotation around the middle of the figure, counter-clockwise, in the DMX grid
 * @param durationSeconds time for one round of the figure
 * @param stepOffset      points by which each further fixture starts later (0 = all in step)
 * @param onceStart       how fixtures with an offset start when the move runs once
 */
public record MoveSpec(String name, long figureId, List<MoveFixture> fixtures, double sizeX, double sizeY,
		double shiftX, double shiftY, double rotationDegrees, double durationSeconds, MoveRepeat repeat,
		int stepOffset, OnceStart onceStart) {

	public static final double MAX_SIZE = 4;
	public static final double MIN_DURATION = 0.1;
	public static final double MAX_DURATION = 3600;
	public static final int MAX_OFFSET = 1000;
	public static final double DEFAULT_DURATION = 8;

	public MoveSpec {
		Objects.requireNonNull(fixtures, "fixtures");
		Objects.requireNonNull(repeat, "repeat");
		Objects.requireNonNull(onceStart, "onceStart");
		fixtures = List.copyOf(fixtures);
		var ids = new HashSet<Long>();
		new Validation()
				.requireText(name, "name")
				.require(figureId > 0, "figureId is missing")
				.require(fixtures.stream().allMatch(f -> ids.add(f.fixtureId())), "a fixture is in the move twice")
				.require(sizeX >= 0 && sizeX <= MAX_SIZE && sizeY >= 0 && sizeY <= MAX_SIZE,
						"size must be between 0 and " + Math.round(MAX_SIZE * 100) + " %")
				.require(shiftX >= -1 && shiftX <= 1 && shiftY >= -1 && shiftY <= 1,
						"shift must be between -255 and 255")
				.require(rotationDegrees >= -360 && rotationDegrees <= 360, "rotation must be between -360 and 360°")
				.require(durationSeconds >= MIN_DURATION && durationSeconds <= MAX_DURATION,
						"duration must be between " + MIN_DURATION + " and " + Math.round(MAX_DURATION) + " s")
				.require(stepOffset >= 0 && stepOffset <= MAX_OFFSET,
						"offset must be between 0 and " + MAX_OFFSET + " steps")
				.throwIfInvalid();
		name = name.strip();
	}

	/** A new move: the figure as drawn, 8 s per round, looping, all fixtures in step. */
	public static MoveSpec of(String name, long figureId, List<MoveFixture> fixtures) {
		return new MoveSpec(name, figureId, fixtures, 1, 1, 0, 0, 0, DEFAULT_DURATION, MoveRepeat.LOOP, 0,
				OnceStart.SEQUENTIAL);
	}

	public MoveSpec withName(String newName) {
		return new MoveSpec(newName, figureId, fixtures, sizeX, sizeY, shiftX, shiftY, rotationDegrees,
				durationSeconds, repeat, stepOffset, onceStart);
	}

	public MoveSpec withFixtures(List<MoveFixture> newFixtures) {
		return new MoveSpec(name, figureId, newFixtures, sizeX, sizeY, shiftX, shiftY, rotationDegrees,
				durationSeconds, repeat, stepOffset, onceStart);
	}

	public List<Long> fixtureIds() {
		return fixtures.stream().map(MoveFixture::fixtureId).toList();
	}
}
