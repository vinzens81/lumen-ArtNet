package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Chase.Direction;
import de.vinzens.lumen.domain.composition.StepSequence.PlayMode;
import de.vinzens.lumen.domain.composition.StepSequence.Step;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PatternTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute GREEN = Attribute.of(ChannelFunction.GREEN);
	private static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);

	@ParameterizedTest(name = "{0} at {1} s: fixture {2} = {3}")
	@CsvSource({
			// direction, seconds, index, expected (4 fixtures, 1 s per step, width 1)
			"FORWARD,    0.0, 0, 1",
			"FORWARD,    0.5, 1, 0",
			"FORWARD,    1.0, 1, 1",
			"FORWARD,    4.2, 0, 1",
			"BACKWARD,   1.0, 3, 1",
			"BACKWARD,   1.0, 1, 0",
			"PING_PONG,  3.0, 3, 1",
			"PING_PONG,  4.0, 2, 1",
			"PING_PONG,  6.0, 0, 1",
	})
	void chaseMovesTheHeadStepByStep(Direction direction, double seconds, int index, double expected) {
		var chase = new Chase(1, 1, direction, 0, 0, 1);
		assertThat(dimmer(chase.valuesAt(seconds, index, 4, 0))).isEqualTo(expected);
	}

	@Test
	void chaseWidthMinMaxAndSoftness() {
		var wide = new Chase(1, 2, Direction.FORWARD, 0, 0.1, 0.9);
		assertThat(dimmer(wide.valuesAt(0, 1, 4, 0))).isEqualTo(0.9);
		assertThat(dimmer(wide.valuesAt(0, 2, 4, 0))).isEqualTo(0.1);
		assertThat(dimmer(wide.valuesAt(0, 3, 4, 0))).as("wraps around").isEqualTo(0.1);
		assertThat(dimmer(wide.valuesAt(3, 0, 4, 0))).as("head on 3, width 2 covers 0").isEqualTo(0.9);

		var soft = new Chase(1, 1, Direction.FORWARD, 1, 0, 1);
		assertThat(dimmer(soft.valuesAt(0.25, 0, 4, 0))).isCloseTo(0.75, within(1e-9));
		assertThat(dimmer(soft.valuesAt(0.25, 1, 4, 0))).isCloseTo(0.25, within(1e-9));
	}

	@Test
	void randomChaseIsReproducible() {
		var chase = new Chase(0.5, 1, Direction.RANDOM, 0, 0, 1);
		for (double t = 0; t < 10; t += 0.5) {
			var lit = 0;
			for (int i = 0; i < 5; i++) {
				lit += (int) dimmer(chase.valuesAt(t, i, 5, 0));
				assertThat(chase.valuesAt(t, i, 5, 0)).isEqualTo(chase.valuesAt(t, i, 5, 0));
			}
			assertThat(lit).isEqualTo(1);
		}
	}

	@Test
	void colorCycleHoldsThenFades() {
		var red = new Color(1, 0, 0);
		var blue = new Color(0, 0, 1);
		var cycle = new ColorCycle(List.of(red, blue), 2, 2, 0, 1);

		assertThat(color(cycle.valuesAt(1, 0, 1, 0))).containsExactly(1, 0, 0);
		assertThat(color(cycle.valuesAt(3, 0, 1, 0))).containsExactly(0.5, 0, 0.5);
		assertThat(color(cycle.valuesAt(5, 0, 1, 0))).containsExactly(0, 0, 1);
		assertThat(color(cycle.valuesAt(7, 0, 1, 0))).as("back to red").containsExactly(0.5, 0, 0.5);
		assertThat(cycle.fallbackIntensity()).contains(1.0);

		var spread = new ColorCycle(List.of(red, blue), 2, 0, 1, 1);
		assertThat(color(spread.valuesAt(0, 1, 2, 0))).as("half a cycle ahead").containsExactly(0, 0, 1);
		assertThatThrownBy(() -> new ColorCycle(List.of(red), 1, 1, 0, 1))
				.isInstanceOf(DomainValidationException.class);
	}

	@Test
	void rainbowRunsThroughTheHues() {
		var rainbow = new Rainbow(6, 1, 0, 1);
		assertThat(color(rainbow.valuesAt(0, 0, 1, 0))).containsExactly(1, 0, 0);
		assertThat(color(rainbow.valuesAt(2, 0, 1, 0))).containsExactly(0, 1, 0);
		assertThat(color(rainbow.valuesAt(4, 0, 1, 0))).containsExactly(0, 0, 1);
		assertThat(color(new Rainbow(6, 1, 1, 1).valuesAt(0, 1, 3, 0))).as("spread").containsExactly(0, 1, 0);
	}

	@ParameterizedTest(name = "{0} at phase {1} = {2}")
	@CsvSource({
			"SINE,     0.0,  0",
			"SINE,     0.5,  1",
			"SINE,     0.25, 0.5",
			"TRIANGLE, 0.25, 0.5",
			"TRIANGLE, 0.75, 0.5",
			"SAWTOOTH, 0.75, 0.75",
			"SQUARE,   0.25, 0",
			"SQUARE,   0.75, 1",
	})
	void waveShapes(Wave.Shape shape, double phase, double expected) {
		var wave = new Wave(shape, 2, 0, 1, 0);
		assertThat(dimmer(wave.valuesAt(phase * 2, 0, 1, 0))).isCloseTo(expected, within(1e-9));
	}

	@Test
	void waveSpreadDelaysLaterFixturesAndHonorsMinMax() {
		var wave = new Wave(Wave.Shape.SAWTOOTH, 4, 0.2, 0.6, 1);
		assertThat(dimmer(wave.valuesAt(2, 0, 2, 0))).isCloseTo(0.4, within(1e-9));
		assertThat(dimmer(wave.valuesAt(2, 1, 2, 0))).as("half a period later").isCloseTo(0.2, within(1e-9));
	}

	@Test
	void flickerIsRelativeReproducibleAndWithinItsStrength() {
		for (var character : Flicker.Character.values()) {
			var flicker = new Flicker(character, 0.5, 1, 42);
			assertThat(flicker.intensityMode()).isEqualTo(IntensityMode.RELATIVE);
			var min = 1.0;
			var max = 0.0;
			for (double t = 0; t < 20; t += 0.01) {
				var factor = dimmer(flicker.valuesAt(t, 0, 1, 0));
				assertThat(factor).isBetween(0.5, 1.0);
				assertThat(factor).isEqualTo(dimmer(flicker.valuesAt(t, 0, 1, 0)));
				min = Math.min(min, factor);
				max = Math.max(max, factor);
			}
			assertThat(max - min).as(character + " moves").isGreaterThan(0.1);
		}
		assertThat(new Flicker(Flicker.Character.CANDLE, 0.5, 1, 1).valuesAt(3.3, 0, 1, 0))
				.as("other seed").isNotEqualTo(new Flicker(Flicker.Character.CANDLE, 0.5, 1, 2).valuesAt(3.3, 0, 1, 0));
	}

	@Test
	void stepSequenceFadesHoldsAndLoops() {
		var steps = new StepSequence(List.of(
				new Step(1, 1, Map.of(1L, values(1.0, 1.0))),
				new Step(1, 0, Map.of(2L, values(0.5, 0.0)))), PlayMode.LOOP);

		assertThat(steps.ownFixtures()).contains(List.of(1L, 2L));
		// step 1: fade 0–1 s from step 2 (fixture 1 dark there), hold 1–2 s
		assertThat(dimmer(steps.valuesAt(0.5, 0, 0, 1))).isCloseTo(0.5, within(1e-9));
		assertThat(dimmer(steps.valuesAt(1.5, 0, 0, 1))).isEqualTo(1.0);
		assertThat(dimmer(steps.valuesAt(1.5, 0, 0, 2))).as("not in step 1: dark").isEqualTo(0.0);
		// step 2: no fade, hold 2–3 s
		assertThat(dimmer(steps.valuesAt(2.5, 0, 0, 1))).isEqualTo(0.0);
		assertThat(dimmer(steps.valuesAt(2.5, 0, 0, 2))).isEqualTo(0.5);
		assertThat(steps.valuesAt(2.5, 0, 0, 1).get(RED)).as("color stays").contains(1.0);
		// loop
		assertThat(dimmer(steps.valuesAt(3.5, 0, 0, 1))).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void pingPongPlaysTheStepsBackwards() {
		var steps = new StepSequence(List.of(
				new Step(1, 0, Map.of(1L, values(1, 0))),
				new Step(1, 0, Map.of(2L, values(1, 0))),
				new Step(1, 0, Map.of(3L, values(1, 0)))), PlayMode.PING_PONG);
		// order 1, 2, 3, 2, 1, 2, …
		assertThat(dimmer(steps.valuesAt(3.5, 0, 0, 2))).isEqualTo(1.0);
		assertThat(dimmer(steps.valuesAt(4.5, 0, 0, 1))).isEqualTo(1.0);
	}

	private static FixtureValues values(double dimmer, double red) {
		return new FixtureValues(Map.of(DIMMER, dimmer, RED, red));
	}

	private static double dimmer(FixtureValues values) {
		return values.get(DIMMER).orElseThrow();
	}

	private static double[] color(FixtureValues values) {
		return new double[] {round(values.get(RED)), round(values.get(GREEN)), round(values.get(BLUE))};
	}

	private static double round(java.util.Optional<Double> value) {
		return Math.round(value.orElseThrow() * 1e6) / 1e6;
	}
}
