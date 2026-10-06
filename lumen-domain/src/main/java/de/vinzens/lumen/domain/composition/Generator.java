package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns an effect (chase, color cycle, rainbow, wave, flicker) into the steps of an ordinary composition. The
 * effect is sampled at the points where it changes direction or color, and the steps fade linearly in between –
 * close to the effect, and afterwards every step can be edited like a recorded one.
 *
 * <p>Pure functions: the same input always gives the same steps (flicker uses its seed).
 */
public final class Generator {

	static final int RAINBOW_STEPS = 12;
	static final int WAVE_STEPS = 16;
	static final int FLICKER_STEPS = 48;
	static final int SAMPLES_PER_COLOR = 4;
	static final int MIN_RANDOM_STEPS = 16;

	private Generator() {
	}

	/**
	 * @param fixtureIds the target in order
	 * @param perHead    every head of a multi-pixel fixture is an element of its own
	 */
	public static GeneratedSteps generate(Pattern pattern, List<Long> fixtureIds, boolean perHead,
			FixtureAttributes attributes) {
		var target = new Sampler(fixtureIds, perHead, attributes);
		return switch (pattern) {
			case Chase chase -> chase(chase, target);
			case ColorCycle cycle -> colorCycle(cycle, target);
			case Rainbow rainbow -> rainbow(rainbow, target);
			case Wave wave -> wave(wave, target);
			case Flicker flicker -> flicker(flicker, target);
			case StepSequence steps -> throw new IllegalArgumentException("a step sequence is not generated");
		};
	}

	private static GeneratedSteps chase(Chase chase, Sampler target) {
		var count = target.elementCount();
		var step = chase.stepSeconds();
		if (chase.direction() == Chase.Direction.RANDOM) {
			var hard = new Chase(step, chase.width(), Chase.Direction.RANDOM, 0, chase.min(), chase.max());
			var steps = new ArrayList<GeneratedSteps.Step>();
			for (int i = 0; i < Math.max(MIN_RANDOM_STEPS, 2 * count); i++) {
				steps.add(new GeneratedSteps.Step(target.sample(hard, (i + 0.5) * step)));
			}
			return new GeneratedSteps(steps, 0, step, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
		}
		// the head runs forward or backward; ping-pong is the forward run played back and forth
		var direction = chase.direction() == Chase.Direction.BACKWARD ? Chase.Direction.BACKWARD
				: Chase.Direction.FORWARD;
		var hard = new Chase(step, chase.width(), direction, 0, chase.min(), chase.max());
		var steps = new ArrayList<GeneratedSteps.Step>();
		for (int i = 0; i < count; i++) {
			steps.add(new GeneratedSteps.Step(target.sample(hard, (i + 0.5) * step)));
		}
		var playMode = chase.direction() == Chase.Direction.PING_PONG ? StepSequence.PlayMode.PING_PONG
				: StepSequence.PlayMode.LOOP;
		return new GeneratedSteps(steps, chase.softness() * step, (1 - chase.softness()) * step, playMode,
				IntensityMode.ABSOLUTE);
	}

	private static GeneratedSteps colorCycle(ColorCycle cycle, Sampler target) {
		var period = cycle.holdSeconds() + cycle.fadeSeconds();
		var colors = cycle.colors().size();
		var steps = new ArrayList<GeneratedSteps.Step>();
		if (cycle.spread() == 0) {
			// exactly the cycle: each color fades in from the one before, then holds
			for (int i = 0; i < colors; i++) {
				steps.add(new GeneratedSteps.Step(withIntensity(target.sample(cycle, i * period), cycle.intensity(),
						target)));
			}
			return new GeneratedSteps(steps, cycle.fadeSeconds(), cycle.holdSeconds(), StepSequence.PlayMode.LOOP,
					IntensityMode.ABSOLUTE);
		}
		// spread fixtures are at different colors at the same time: sample the cycle evenly
		var samples = colors * SAMPLES_PER_COLOR;
		var interval = period * colors / samples;
		for (int i = 0; i < samples; i++) {
			steps.add(new GeneratedSteps.Step(withIntensity(target.sample(cycle, i * interval), cycle.intensity(),
					target)));
		}
		return new GeneratedSteps(steps, interval, 0, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
	}

	private static GeneratedSteps rainbow(Rainbow rainbow, Sampler target) {
		var interval = rainbow.cycleSeconds() / RAINBOW_STEPS;
		var steps = new ArrayList<GeneratedSteps.Step>();
		for (int i = 0; i < RAINBOW_STEPS; i++) {
			steps.add(new GeneratedSteps.Step(withIntensity(target.sample(rainbow, i * interval), rainbow.intensity(),
					target)));
		}
		return new GeneratedSteps(steps, interval, 0, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
	}

	private static GeneratedSteps wave(Wave wave, Sampler target) {
		var period = wave.periodSeconds();
		var steps = new ArrayList<GeneratedSteps.Step>();
		if (wave.spread() == 0 && wave.shape() == Wave.Shape.SQUARE) {
			steps.add(new GeneratedSteps.Step(target.sample(wave, 0.25 * period)));
			steps.add(new GeneratedSteps.Step(target.sample(wave, 0.75 * period)));
			return new GeneratedSteps(steps, 0, period / 2, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
		}
		if (wave.spread() == 0 && wave.shape() == Wave.Shape.TRIANGLE) {
			steps.add(new GeneratedSteps.Step(target.sample(wave, 0)));
			steps.add(new GeneratedSteps.Step(target.sample(wave, 0.5 * period)));
			return new GeneratedSteps(steps, period / 2, 0, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
		}
		var interval = period / WAVE_STEPS;
		var hard = wave.shape() == Wave.Shape.SQUARE;
		for (int i = 0; i < WAVE_STEPS; i++) {
			var values = target.sample(wave, (i + (hard ? 0.5 : 0)) * interval);
			// a sawtooth drops back to its low at once
			var jump = wave.shape() == Wave.Shape.SAWTOOTH && wave.spread() == 0 && i == 0;
			steps.add(new GeneratedSteps.Step(values, jump ? Optional.of(0.0) : Optional.empty(),
					jump ? Optional.of(interval) : Optional.empty()));
		}
		return hard
				? new GeneratedSteps(steps, 0, interval, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE)
				: new GeneratedSteps(steps, interval, 0, StepSequence.PlayMode.LOOP, IntensityMode.ABSOLUTE);
	}

	private static GeneratedSteps flicker(Flicker flicker, Sampler target) {
		// one random point per step, as the flicker itself has one per time unit
		var interval = 1 / (flicker.character().rateHz() * flicker.speed());
		var hard = flicker.character() == Flicker.Character.BROKEN_TUBE;
		var steps = new ArrayList<GeneratedSteps.Step>();
		for (int i = 0; i < FLICKER_STEPS; i++) {
			steps.add(new GeneratedSteps.Step(target.sample(flicker, (i + (hard ? 0.5 : 0)) * interval)));
		}
		return hard
				? new GeneratedSteps(steps, 0, interval, StepSequence.PlayMode.LOOP, IntensityMode.RELATIVE)
				: new GeneratedSteps(steps, interval, 0, StepSequence.PlayMode.LOOP, IntensityMode.RELATIVE);
	}

	/** Color effects light their fixtures with their own intensity (0 = leave the intensity to others). */
	private static Map<Long, FixtureValues> withIntensity(Map<Long, FixtureValues> values, double intensity,
			Sampler target) {
		if (intensity <= 0) {
			return values;
		}
		var result = new HashMap<Long, FixtureValues>();
		values.forEach((fixtureId, fixtureValues) -> result.put(fixtureId,
				target.attributes().has(fixtureId, Values.DIMMER)
						? fixtureValues.with(Values.intensity(intensity))
						: fixtureValues));
		return result;
	}

	/** The target of an effect: samples its values at a point in time, per fixture (and head). */
	private record Sampler(List<Long> fixtureIds, boolean perHead, FixtureAttributes attributes) {

		Map<Long, FixtureValues> sample(Pattern pattern, double seconds) {
			var composition = new ActiveComposition(0, Optional.empty(), pattern, fixtureIds, 1, 1, false, false,
					perHead);
			return CompositionMixer.contribution(composition, seconds, attributes);
		}

		int elementCount() {
			if (!perHead) {
				return fixtureIds.size();
			}
			return fixtureIds.stream().mapToInt(id -> Math.max(1, attributes.heads(id))).sum();
		}
	}
}
