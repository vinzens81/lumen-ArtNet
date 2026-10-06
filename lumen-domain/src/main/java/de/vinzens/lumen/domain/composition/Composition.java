package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import de.vinzens.lumen.domain.look.Look;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The second level of the show: a sequence of 1–n steps, each playing a look, fading and holding by the default
 * times of the composition or the step's own. A composition can be part of any number of scenes; a change applies to
 * all of them.
 */
public record Composition(long id, long projectId, CompositionSpec spec, List<CompositionStep> steps) {

	public Composition {
		Objects.requireNonNull(spec, "spec");
		steps = List.copyOf(steps);
		validateSteps(spec, steps);
	}

	/** Every step must last at least {@link CompositionSpec#MIN_STEP_SECONDS} with its resolved times. */
	public static void validateSteps(CompositionSpec spec, List<CompositionStep> steps) {
		var validation = new Validation();
		for (int i = 0; i < steps.size(); i++) {
			var step = steps.get(i);
			validation.require(step.fadeOr(spec.fadeSeconds()) + step.holdOr(spec.holdSeconds())
					>= CompositionSpec.MIN_STEP_SECONDS, "step " + (i + 1) + ": fade and hold must together be at least "
					+ CompositionSpec.MIN_STEP_SECONDS + " s");
		}
		validation.throwIfInvalid();
	}

	/**
	 * The steps as the engine plays them, with their times resolved. A look that no longer exists plays as an empty
	 * step (everything of the composition dark).
	 */
	public StepSequence sequence(Map<Long, Look> looks) {
		var sequenceSteps = new ArrayList<StepSequence.Step>();
		for (var step : steps) {
			var look = looks.getOrDefault(step.lookId(), Look.EMPTY);
			sequenceSteps.add(new StepSequence.Step(step.holdOr(spec.holdSeconds()), step.fadeOr(spec.fadeSeconds()),
					look.values()));
		}
		return new StepSequence(sequenceSteps, spec.playMode().sequenceMode(), spec.intensityMode());
	}

	/** Ids of the looks the steps play, in step order (a look may appear more than once). */
	public List<Long> lookIds() {
		return steps.stream().map(CompositionStep::lookId).toList();
	}
}
