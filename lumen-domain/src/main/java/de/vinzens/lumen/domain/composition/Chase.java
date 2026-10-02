package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Objects;

/**
 * A light running along the target: {@code width} fixtures are on at a time, the head moves one fixture per step.
 *
 * @param stepSeconds time per step
 * @param width       fixtures on at the same time (at least 1)
 * @param softness    0 = hard on/off, 1 = fixtures fade in and out around the head
 * @param min         intensity of the fixtures that are off
 * @param max         intensity of the fixtures that are on
 */
public record Chase(double stepSeconds, int width, Direction direction, double softness, double min, double max)
		implements Pattern {

	public enum Direction {
		FORWARD, BACKWARD, PING_PONG, RANDOM
	}

	public Chase {
		Objects.requireNonNull(direction, "direction");
		new Validation()
				.require(stepSeconds >= 0.02 && stepSeconds <= 3600, "stepSeconds must be between 0.02 and 3600")
				.require(width >= 1, "width must be at least 1")
				.require(softness >= 0 && softness <= 1, "softness must be between 0 and 1")
				.require(min >= 0 && min <= 1 && max >= 0 && max <= 1, "min and max must be between 0 and 1")
				.throwIfInvalid();
	}

	@Override
	public PatternKind kind() {
		return PatternKind.CHASE;
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		if (count <= 0) {
			return FixtureValues.EMPTY;
		}
		var steps = Math.max(0, seconds) / stepSeconds;
		var step = (long) Math.floor(steps);
		var head = headAt(step, count);
		var hard = isOn(index, head, count) ? 1.0 : 0.0;
		var level = hard;
		if (softness > 0 && direction != Direction.RANDOM) {
			// continuous head position: fixtures brighten as the head approaches and fade after it passed
			var next = headAt(step + 1, count);
			var progress = steps - step;
			var soft = Math.max(onLevel(index, head, count) * (1 - progress), onLevel(index, next, count) * progress);
			level = hard * (1 - softness) + soft * softness;
		}
		return Values.intensity(min + (max - min) * level);
	}

	/** Index of the first fixture that is on in the given step. */
	private int headAt(long step, int count) {
		return switch (direction) {
			case FORWARD -> (int) Math.floorMod(step, count);
			case BACKWARD -> (int) Math.floorMod(-step, count);
			case PING_PONG -> {
				if (count == 1) {
					yield 0;
				}
				var period = 2 * (count - 1);
				var position = (int) Math.floorMod(step, period);
				yield position < count ? position : period - position;
			}
			case RANDOM -> (int) Math.floor(Values.random(17, step, count) * count);
		};
	}

	private boolean isOn(int index, int head, int count) {
		return onLevel(index, head, count) > 0;
	}

	/** 1 if the fixture is within {@code width} fixtures after the head (wrapping around), else 0. */
	private double onLevel(int index, int head, int count) {
		var distance = Math.floorMod(index - head, count);
		return distance < width ? 1 : 0;
	}
}
