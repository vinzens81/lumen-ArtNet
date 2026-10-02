package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Objects;

/**
 * Irregular flickering around the base intensity (relative). Random, but with a fixed seed: the same time always
 * gives the same value, so a rehearsal looks like the show.
 *
 * @param strength how far the intensity drops at most (0–1)
 * @param speed    1 = natural speed of the character
 */
public record Flicker(Character character, double strength, double speed, long seed) implements Pattern {

	public enum Character {
		/** gentle, slow movement */
		CANDLE(5, 0.6),
		/** livelier, deeper dips */
		FIRE(11, 1),
		/** mostly on, sometimes drops out */
		BROKEN_TUBE(7, 1);

		private final double rateHz;
		private final double depth;

		Character(double rateHz, double depth) {
			this.rateHz = rateHz;
			this.depth = depth;
		}
	}

	public Flicker {
		Objects.requireNonNull(character, "character");
		new Validation()
				.require(strength >= 0 && strength <= 1, "strength must be between 0 and 1")
				.require(speed >= 0.1 && speed <= 10, "speed must be between 0.1 and 10")
				.throwIfInvalid();
	}

	@Override
	public PatternKind kind() {
		return PatternKind.FLICKER;
	}

	@Override
	public IntensityMode intensityMode() {
		return IntensityMode.RELATIVE;
	}

	/** The intensity factor (1 = base intensity). */
	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		var t = Math.max(0, seconds) * character.rateHz * speed;
		var drop = switch (character) {
			case CANDLE -> noise(t, index, 0);
			case FIRE -> 0.65 * noise(t, index, 0) + 0.35 * noise(t * 2.3, index, 1);
			case BROKEN_TUBE -> Values.random(seed, index, (long) Math.floor(t)) < 0.12 ? 1 : 0.05 * noise(t, index, 0);
		};
		return Values.intensity(1 - strength * character.depth * drop);
	}

	/** Smooth random value 0–1: random points per time unit, eased in between. */
	private double noise(double t, int index, int octave) {
		var step = (long) Math.floor(t);
		var a = Values.random(seed + octave, index, step);
		var b = Values.random(seed + octave, index, step + 1);
		var f = t - step;
		var eased = f * f * (3 - 2 * f);
		return a + (b - a) * eased;
	}
}
