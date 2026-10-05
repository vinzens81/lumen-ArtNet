package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;

/**
 * Editable properties of a composition, in the library as well as a copy in a scene.
 *
 * @param level       how strongly the composition shows (0–1)
 * @param speedFactor multiplies the tempo (2 = twice as fast)
 * @param restart     in a scene: start from the beginning at GO instead of continuing a running composition of the
 *                    same origin
 * @param once        run through once and stay at the end (e.g. a sunrise) instead of repeating
 * @param perHead     every head of a multi-pixel fixture is an element of its own (a chase runs along a LED bar)
 */
public record CompositionSpec(String name, Pattern pattern, Target target, double level, double speedFactor,
		boolean restart, boolean once, boolean perHead) {

	public CompositionSpec {
		Objects.requireNonNull(pattern, "pattern");
		Objects.requireNonNull(target, "target");
		var steps = pattern instanceof StepSequence;
		new Validation()
				.requireText(name, "name")
				.require(level >= 0 && level <= 1, "level must be between 0 and 1")
				.require(speedFactor >= 0.05 && speedFactor <= 20, "speedFactor must be between 0.05 and 20")
				.require(!steps || target instanceof Target.Steps,
						"a step sequence runs on the fixtures in its steps, it has no other target")
				.require(steps || !(target instanceof Target.Steps), "a " + pattern.kind().id()
						+ " composition needs a group or a list of fixtures as target")
				.throwIfInvalid();
		name = name.strip();
	}

	/** A composition on whole fixtures. */
	public CompositionSpec(String name, Pattern pattern, Target target, double level, double speedFactor,
			boolean restart, boolean once) {
		this(name, pattern, target, level, speedFactor, restart, once, false);
	}

	/** A new, repeating composition with level 100 % and normal speed. */
	public static CompositionSpec of(String name, Pattern pattern, Target target) {
		return new CompositionSpec(name, pattern, target, 1, 1, false, false);
	}

	/** The same composition with other pattern and target (e.g. taken from the library), keeping the rest. */
	public CompositionSpec withContent(String newName, Pattern newPattern, Target newTarget) {
		return new CompositionSpec(newName, newPattern, newTarget, level, speedFactor, restart, once, perHead);
	}
}
