package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The second level of the show: a sequence of 1–n steps, each playing a look, fading and holding by the default
 * times of the composition or the step's own. Steps marked parallel join the row of the step before them and play
 * at the same time. A composition can be part of any number of scenes; a change applies to all of them.
 */
public record Composition(long id, long projectId, CompositionSpec spec, List<CompositionStep> steps) {

	public Composition {
		Objects.requireNonNull(spec, "spec");
		steps = List.copyOf(steps);
		validateSteps(spec, steps);
	}

	/**
	 * Every step must last at least {@link CompositionSpec#MIN_STEP_SECONDS} with its resolved times, and the first
	 * step cannot run in parallel with a step before it.
	 */
	public static void validateSteps(CompositionSpec spec, List<CompositionStep> steps) {
		var validation = new Validation();
		validation.require(steps.isEmpty() || !steps.getFirst().parallel(), "the first step cannot be parallel");
		for (int i = 0; i < steps.size(); i++) {
			var step = steps.get(i);
			validation.require(step.parallel() || step.fadeOr(spec.fadeSeconds()) + step.holdOr(spec.holdSeconds())
					>= CompositionSpec.MIN_STEP_SECONDS, "step " + (i + 1) + ": fade and hold must together be at least "
					+ CompositionSpec.MIN_STEP_SECONDS + " s");
		}
		validation.throwIfInvalid();
	}

	/**
	 * The steps as the engine plays them, with their times resolved: one step per row, playing the looks of all
	 * parallel steps together (intensity HTP, other attributes from the later step). A look that no longer exists
	 * plays as an empty step (everything of the composition dark).
	 */
	public StepSequence sequence(Map<Long, Look> looks) {
		var sequenceSteps = new ArrayList<StepSequence.Step>();
		for (var row : rows()) {
			var first = row.getFirst();
			var values = new HashMap<Long, FixtureValues>();
			for (var step : row) {
				looks.getOrDefault(step.lookId(), Look.EMPTY).values()
						.forEach((fixtureId, fixtureValues) -> values.merge(fixtureId, fixtureValues, Composition::combine));
			}
			sequenceSteps.add(new StepSequence.Step(first.holdOr(spec.holdSeconds()), first.fadeOr(spec.fadeSeconds()),
					values));
		}
		return new StepSequence(sequenceSteps, spec.playMode().sequenceMode(), spec.intensityMode());
	}

	/** The steps grouped into rows: a step starts a new row unless it runs parallel to the one before. */
	public List<List<CompositionStep>> rows() {
		var rows = new ArrayList<List<CompositionStep>>();
		for (var step : steps) {
			if (step.parallel() && !rows.isEmpty()) {
				rows.getLast().add(step);
			} else {
				rows.add(new ArrayList<>(List.of(step)));
			}
		}
		return rows.stream().map(List::copyOf).toList();
	}

	/** Two looks on one fixture in the same row: intensity HTP, everything else from the later look. */
	private static FixtureValues combine(FixtureValues earlier, FixtureValues later) {
		var result = new HashMap<>(earlier.values());
		later.values().forEach((attribute, value) -> {
			if (attribute.function().defaultFade() == FadeBehavior.INTENSITY) {
				result.merge(attribute, value, Math::max);
			} else {
				result.put(attribute, value);
			}
		});
		return new FixtureValues(result);
	}

	/** Ids of the looks the steps play, in step order (a look may appear more than once). */
	public List<Long> lookIds() {
		return steps.stream().map(CompositionStep::lookId).toList();
	}
}
