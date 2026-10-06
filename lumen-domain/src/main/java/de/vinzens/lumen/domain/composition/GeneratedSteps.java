package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Steps a generator made from an effect (chase, rainbow, …): an ordinary step sequence with default times and
 * per-step overrides, ready to be stored as a composition and edited like any other.
 *
 * @param fadeSeconds default fade into a step
 * @param holdSeconds default hold of a step
 */
public record GeneratedSteps(List<Step> steps, double fadeSeconds, double holdSeconds,
		StepSequence.PlayMode playMode, IntensityMode intensityMode) {

	/**
	 * @param values values per fixture (head attributes like {@code red@3} for multi-pixel fixtures)
	 * @param fadeSeconds overrides the default fade, empty = default
	 * @param holdSeconds overrides the default hold, empty = default
	 */
	public record Step(Map<Long, FixtureValues> values, Optional<Double> fadeSeconds, Optional<Double> holdSeconds) {

		public Step {
			values = Map.copyOf(values);
			Objects.requireNonNull(fadeSeconds, "fadeSeconds");
			Objects.requireNonNull(holdSeconds, "holdSeconds");
		}

		public Step(Map<Long, FixtureValues> values) {
			this(values, Optional.empty(), Optional.empty());
		}
	}

	public GeneratedSteps {
		steps = List.copyOf(steps);
		Objects.requireNonNull(playMode, "playMode");
		Objects.requireNonNull(intensityMode, "intensityMode");
		new Validation()
				.require(fadeSeconds >= 0 && holdSeconds >= 0, "fade and hold must not be negative")
				.throwIfInvalid();
	}

	/** The steps with their times resolved, as the engine plays them. */
	public StepSequence sequence() {
		return new StepSequence(steps.stream()
				.map(step -> new StepSequence.Step(step.holdSeconds().orElse(holdSeconds),
						step.fadeSeconds().orElse(fadeSeconds), step.values()))
				.toList(), playMode, intensityMode);
	}
}
