package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * A step of a composition: plays a look, with the composition's default times unless overridden.
 * <p>
 * A step can run in parallel with the step before it: consecutive parallel steps form one row that starts at the same
 * time and plays all its looks together. The row takes its times from its first step, so a parallel step has no times
 * of its own.
 *
 * @param fadeSeconds fade into this step, empty = the composition's default
 * @param holdSeconds hold of this step, empty = the composition's default
 * @param parallel    starts together with the step before it instead of after it
 */
public record CompositionStep(long lookId, Optional<Double> fadeSeconds, Optional<Double> holdSeconds,
		boolean parallel) {

	public CompositionStep {
		Objects.requireNonNull(fadeSeconds, "fadeSeconds");
		Objects.requireNonNull(holdSeconds, "holdSeconds");
		new Validation()
				.require(fadeSeconds.map(CompositionSpec::validTime).orElse(true)
						&& holdSeconds.map(CompositionSpec::validTime).orElse(true),
						"fade and hold of a step must be between 0 and " + (int) CompositionSpec.MAX_SECONDS + " s")
				.require(!parallel || (fadeSeconds.isEmpty() && holdSeconds.isEmpty()),
						"a parallel step takes the times of its row and has no times of its own")
				.throwIfInvalid();
	}

	/** A step that starts after the step before it. */
	public CompositionStep(long lookId, Optional<Double> fadeSeconds, Optional<Double> holdSeconds) {
		this(lookId, fadeSeconds, holdSeconds, false);
	}

	/** A step with the default times. */
	public static CompositionStep of(long lookId) {
		return new CompositionStep(lookId, Optional.empty(), Optional.empty());
	}

	/** A step that runs together with the step before it. */
	public static CompositionStep parallelOf(long lookId) {
		return new CompositionStep(lookId, Optional.empty(), Optional.empty(), true);
	}

	/** The same step with another look. */
	public CompositionStep withLook(long newLookId) {
		return new CompositionStep(newLookId, fadeSeconds, holdSeconds, parallel);
	}

	public double fadeOr(double defaultSeconds) {
		return fadeSeconds.orElse(defaultSeconds);
	}

	public double holdOr(double defaultSeconds) {
		return holdSeconds.orElse(defaultSeconds);
	}
}
