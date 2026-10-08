package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.List;
import java.util.Optional;

/**
 * What a composition does, as a pure function of time: no clock, no state. The time is already scaled by the
 * speed factor of the composition.
 */
public sealed interface Pattern permits Chase, ColorCycle, Rainbow, Wave, Flicker, StepSequence,
		MovePattern {

	PatternKind kind();

	/**
	 * Values for one fixture of the target.
	 *
	 * @param seconds   time since the composition started
	 * @param index     position of the fixture in the target order
	 * @param count     number of fixtures in the target
	 * @param fixtureId the fixture, for patterns that store values per fixture (step sequences)
	 */
	FixtureValues valuesAt(double seconds, int index, int count, long fixtureId);

	/** How the intensity of the pattern combines with the intensity of the scene base. */
	default IntensityMode intensityMode() {
		return IntensityMode.ABSOLUTE;
	}

	/** Intensity for fixtures without a base intensity, for patterns that only set the color. */
	default Optional<Double> fallbackIntensity() {
		return Optional.empty();
	}

	/**
	 * Time after which a composition that runs only once stands still at its end, e.g. a sunrise that stays up.
	 * Empty for patterns without an end (random chase, flicker).
	 *
	 * @param count number of fixtures in the target
	 */
	default Optional<Double> onceSeconds(int count) {
		return Optional.empty();
	}

	/** A moment before {@code seconds}: the end of a cycle without wrapping around to its start. */
	static double justBefore(double seconds) {
		return Math.max(0, seconds - 1e-6);
	}

	/** Fixtures the pattern itself defines (step sequences); otherwise the composition target is used. */
	default Optional<List<Long>> ownFixtures() {
		return Optional.empty();
	}

	/** Position in a cycle, 0 ≤ result < 1. */
	static double cycle(double value) {
		return value - Math.floor(value);
	}

	/** Phase offset of a fixture for a spread of 0 (all the same) to 1 (evenly spread over one cycle). */
	static double spreadOffset(double spread, int index, int count) {
		return count <= 0 ? 0 : spread * index / count;
	}
}
