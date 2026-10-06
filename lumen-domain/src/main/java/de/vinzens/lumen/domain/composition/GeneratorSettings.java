package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;

/**
 * What the steps of a composition were generated from, kept only to generate them again with changed settings
 * ("Regenerate"); playing the composition does not use it.
 *
 * @param perHead every head of a multi-pixel fixture is an element of its own (a chase runs along a LED bar)
 */
public record GeneratorSettings(Pattern effect, Target target, boolean perHead) {

	public GeneratorSettings {
		Objects.requireNonNull(effect, "effect");
		Objects.requireNonNull(target, "target");
		new Validation()
				.require(!(effect instanceof StepSequence), "a step sequence is not a generator")
				.throwIfInvalid();
	}
}
