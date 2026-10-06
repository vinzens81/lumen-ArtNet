package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * A free sequence of looks (steps), like a chaser. Each step first fades in from the previous step, then holds.
 * A fixture that is not in a step is dark in that step; its other attributes keep their last value.
 */
public record StepSequence(List<Step> steps, PlayMode playMode, IntensityMode intensityMode) implements Pattern {

	public enum PlayMode {
		LOOP, PING_PONG
	}

	/**
	 * @param values normalized values per fixture, recorded from the programmer
	 */
	public record Step(double holdSeconds, double fadeSeconds, Map<Long, FixtureValues> values) {

		public Step {
			values = Map.copyOf(values);
			new Validation()
					.require(holdSeconds >= 0 && fadeSeconds >= 0 && holdSeconds + fadeSeconds >= 0.02,
							"hold and fade of a step must not be negative and together at least 0.02 s")
					.throwIfInvalid();
		}

		double duration() {
			return fadeSeconds + holdSeconds;
		}
	}

	public StepSequence {
		steps = List.copyOf(steps);
		Objects.requireNonNull(playMode, "playMode");
		Objects.requireNonNull(intensityMode, "intensityMode");
	}

	/** Steps that set the intensity. */
	public StepSequence(List<Step> steps, PlayMode playMode) {
		this(steps, playMode, IntensityMode.ABSOLUTE);
	}

	@Override
	public PatternKind kind() {
		return PatternKind.STEPS;
	}

	@Override
	public IntensityMode intensityMode() {
		return intensityMode;
	}

	@Override
	public Optional<List<Long>> ownFixtures() {
		var ids = new TreeSet<Long>();
		steps.forEach(step -> ids.addAll(step.values().keySet()));
		return Optional.of(List.copyOf(ids));
	}

	/** Once through all steps; the last step stays (ping-pong: without going back). */
	@Override
	public Optional<Double> onceSeconds(int count) {
		var total = steps.stream().mapToDouble(Step::duration).sum();
		return steps.isEmpty() ? Optional.empty() : Optional.of(Pattern.justBefore(total));
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		if (steps.isEmpty()) {
			return FixtureValues.EMPTY;
		}
		var order = order();
		var total = 0.0;
		for (var i : order) {
			total += steps.get(i).duration();
		}
		var t = Pattern.cycle(Math.max(0, seconds) / total) * total;
		for (int position = 0; position < order.size(); position++) {
			var step = steps.get(order.get(position));
			if (t < step.duration() || position == order.size() - 1) {
				var previous = steps.get(order.get(Math.floorMod(position - 1, order.size())));
				var progress = step.fadeSeconds() <= 0 ? 1 : Math.min(1, t / step.fadeSeconds());
				return blend(look(previous, fixtureId), look(step, fixtureId), progress);
			}
			t -= step.duration();
		}
		throw new IllegalStateException("unreachable");
	}

	/** Step indexes in playing order: 0..n-1, or 0..n-1..1 for ping-pong. */
	private List<Integer> order() {
		var result = new ArrayList<Integer>();
		for (int i = 0; i < steps.size(); i++) {
			result.add(i);
		}
		if (playMode == PlayMode.PING_PONG) {
			for (int i = steps.size() - 2; i > 0; i--) {
				result.add(i);
			}
		}
		return result;
	}

	/** Values of a fixture in a step: not in the step = dark. */
	private static FixtureValues look(Step step, long fixtureId) {
		var values = step.values().get(fixtureId);
		return values == null ? Values.intensity(0) : values.with(defaultIntensity(values));
	}

	private static FixtureValues defaultIntensity(FixtureValues values) {
		return values.get(Values.DIMMER).isPresent() ? FixtureValues.EMPTY : Values.intensity(0);
	}

	/** Linear blend; attributes only one side has keep that side's value. */
	private static FixtureValues blend(FixtureValues from, FixtureValues to, double progress) {
		var attributes = new HashSet<Attribute>(from.values().keySet());
		attributes.addAll(to.values().keySet());
		var result = new HashMap<Attribute, Double>();
		for (var attribute : attributes) {
			var a = from.get(attribute);
			var b = to.get(attribute);
			result.put(attribute, a.isEmpty() ? b.get() : b.isEmpty() ? a.get()
					: a.get() + (b.get() - a.get()) * progress);
		}
		return new FixtureValues(result);
	}
}
