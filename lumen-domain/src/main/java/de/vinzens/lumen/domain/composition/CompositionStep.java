package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * A step of a composition: plays a look, with the composition's default times unless overridden.
 *
 * @param fadeSeconds fade into this step, empty = the composition's default
 * @param holdSeconds hold of this step, empty = the composition's default
 */
public record CompositionStep(long lookId, Optional<Double> fadeSeconds, Optional<Double> holdSeconds) {

	public CompositionStep {
		Objects.requireNonNull(fadeSeconds, "fadeSeconds");
		Objects.requireNonNull(holdSeconds, "holdSeconds");
		new Validation()
				.require(fadeSeconds.map(CompositionSpec::validTime).orElse(true)
						&& holdSeconds.map(CompositionSpec::validTime).orElse(true),
						"fade and hold of a step must be between 0 and " + (int) CompositionSpec.MAX_SECONDS + " s")
				.throwIfInvalid();
	}

	/** A step with the default times. */
	public static CompositionStep of(long lookId) {
		return new CompositionStep(lookId, Optional.empty(), Optional.empty());
	}

	public double fadeOr(double defaultSeconds) {
		return fadeSeconds.orElse(defaultSeconds);
	}

	public double holdOr(double defaultSeconds) {
		return holdSeconds.orElse(defaultSeconds);
	}
}
