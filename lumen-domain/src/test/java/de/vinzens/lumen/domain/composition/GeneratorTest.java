package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.composition.Chase.Direction;
import de.vinzens.lumen.domain.composition.StepSequence.PlayMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeneratorTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final List<Long> FOUR = List.of(11L, 12L, 13L, 14L);

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"FORWARD", "BACKWARD", "PING_PONG"})
	void aHardChasePlaysExactlyLikeTheChase(Direction direction) {
		var chase = new Chase(0.5, 1, direction, 0, 0.1, 0.9);
		var generated = Generator.generate(chase, FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.steps()).hasSize(4);
		assertThat(generated.playMode()).isEqualTo(direction == Direction.PING_PONG ? PlayMode.PING_PONG : PlayMode.LOOP);
		var sequence = generated.sequence();
		for (double t = 0.05; t < 6; t += 0.25) {
			for (int i = 0; i < FOUR.size(); i++) {
				assertThat(dimmer(sequence.valuesAt(t, i, 4, FOUR.get(i))))
						.as("%s at %.2f s, fixture %d", direction, t, i)
						.isCloseTo(dimmer(chase.valuesAt(t, i, 4, FOUR.get(i))), within(1e-9));
			}
		}
	}

	@Test
	void aSoftChaseFadesFromStepToStep() {
		var generated = Generator.generate(new Chase(1, 1, Direction.FORWARD, 0.4, 0, 1), FOUR, false,
				FixtureAttributes.ALL);

		assertThat(generated.fadeSeconds()).isCloseTo(0.4, within(1e-9));
		assertThat(generated.holdSeconds()).isCloseTo(0.6, within(1e-9));
	}

	@Test
	void aRandomChaseGetsEnoughStepsNotToRepeatAtOnce() {
		var generated = Generator.generate(new Chase(0.2, 1, Direction.RANDOM, 0, 0, 1), FOUR, false,
				FixtureAttributes.ALL);

		assertThat(generated.steps()).hasSize(Generator.MIN_RANDOM_STEPS);
		assertThat(generated.steps()).allSatisfy(step -> assertThat(step.values().values().stream()
				.filter(v -> dimmer(v) > 0.5)).hasSize(1));
		assertThat(Generator.generate(new Chase(0.2, 1, Direction.RANDOM, 0, 0, 1), FOUR, false,
				FixtureAttributes.ALL)).as("reproducible").isEqualTo(generated);
	}

	@Test
	void aChaseRunsAlongTheHeadsOfPixelBars() {
		var bars = new FixtureAttributes() {
			@Override
			public boolean has(long fixtureId, Attribute attribute) {
				return true;
			}

			@Override
			public int heads(long fixtureId) {
				return 3;
			}
		};
		var generated = Generator.generate(new Chase(1, 1, Direction.FORWARD, 0, 0, 1), List.of(1L, 2L), true, bars);

		assertThat(generated.steps()).hasSize(6);
		var fourth = generated.steps().get(3).values();
		assertThat(fourth.get(2L).get(DIMMER.withHead(1))).contains(1.0);
		assertThat(fourth.get(1L).get(DIMMER.withHead(3))).contains(0.0);
	}

	@Test
	void aColorCycleWithoutSpreadBecomesOneStepPerColor() {
		var red = new Color(1, 0, 0);
		var green = new Color(0, 1, 0);
		var blue = new Color(0, 0, 1);
		var cycle = new ColorCycle(List.of(red, green, blue), 2, 1, 0, 0.8);
		var generated = Generator.generate(cycle, FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.steps()).hasSize(3);
		assertThat(generated.fadeSeconds()).isEqualTo(1);
		assertThat(generated.holdSeconds()).isEqualTo(2);
		var second = generated.steps().get(1).values().get(12L);
		assertThat(second.get(Attribute.of(ChannelFunction.GREEN))).contains(1.0);
		assertThat(second.get(RED)).contains(0.0);
		assertThat(second.get(DIMMER)).as("the cycle's own intensity").contains(0.8);
	}

	@Test
	void aColorCycleWithoutIntensityLeavesTheIntensityToOthers() {
		var cycle = new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0, 1)), 1, 1, 0, 0);
		var generated = Generator.generate(cycle, FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.steps().getFirst().values().get(11L).get(DIMMER)).isEmpty();
	}

	@Test
	void spreadEffectsAreSampledEvenly() {
		var cycle = new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0, 1)), 1, 1, 1, 1);
		var generated = Generator.generate(cycle, FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.steps()).hasSize(2 * Generator.SAMPLES_PER_COLOR);
		assertThat(generated.fadeSeconds()).isCloseTo(0.5, within(1e-9));
		assertThat(generated.holdSeconds()).isZero();
	}

	@Test
	void aRainbowRunsThroughTwelveHues() {
		var generated = Generator.generate(new Rainbow(6, 1, 0, 1), FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.steps()).hasSize(Generator.RAINBOW_STEPS);
		assertThat(generated.fadeSeconds()).isCloseTo(0.5, within(1e-9));
		assertThat(generated.steps().getFirst().values().get(11L).get(RED)).contains(1.0);
	}

	@Test
	void wavesKeepTheirShape() {
		var triangle = Generator.generate(new Wave(Wave.Shape.TRIANGLE, 2, 0.2, 1, 0), FOUR, false,
				FixtureAttributes.ALL);
		assertThat(triangle.steps()).hasSize(2);
		assertThat(triangle.fadeSeconds()).isEqualTo(1);
		assertThat(dimmer(triangle.steps().get(1).values().get(11L))).isEqualTo(1);

		var square = Generator.generate(new Wave(Wave.Shape.SQUARE, 2, 0, 1, 0), FOUR, false, FixtureAttributes.ALL);
		assertThat(square.fadeSeconds()).isZero();
		assertThat(square.holdSeconds()).isEqualTo(1);

		var sawtooth = Generator.generate(new Wave(Wave.Shape.SAWTOOTH, 1.6, 0, 1, 0), FOUR, false,
				FixtureAttributes.ALL);
		assertThat(sawtooth.steps()).hasSize(Generator.WAVE_STEPS);
		assertThat(sawtooth.steps().getFirst().fadeSeconds()).as("drops back at once").contains(0.0);
		assertThat(sawtooth.steps().get(1).fadeSeconds()).isEmpty();

		var sine = Generator.generate(new Wave(Wave.Shape.SINE, 1.6, 0, 1, 0), FOUR, false, FixtureAttributes.ALL)
				.sequence();
		assertThat(dimmer(sine.valuesAt(0.8, 0, 4, 11L))).as("top of the sine").isCloseTo(1, within(0.05));
	}

	@Test
	void aFlickerIsRelativeAndReproducible() {
		var flicker = new Flicker(Flicker.Character.CANDLE, 0.5, 1, 7);
		var generated = Generator.generate(flicker, FOUR, false, FixtureAttributes.ALL);

		assertThat(generated.intensityMode()).isEqualTo(IntensityMode.RELATIVE);
		assertThat(generated.steps()).hasSize(Generator.FLICKER_STEPS);
		assertThat(generated.fadeSeconds()).isCloseTo(0.2, within(1e-9));
		assertThat(generated.steps()).allSatisfy(step -> step.values().values()
				.forEach(v -> assertThat(dimmer(v)).isBetween(0.5, 1.0)));
		assertThat(Generator.generate(flicker, FOUR, false, FixtureAttributes.ALL)).isEqualTo(generated);
	}

	@Test
	void veryFastEffectsGetFewerStepsInsteadOfTooShortOnes() {
		var rainbow = Generator.generate(new Rainbow(0.1, 1, 0, 1), FOUR, false, FixtureAttributes.ALL);
		assertThat(rainbow.steps()).hasSize(5);
		assertThat(rainbow.fadeSeconds()).isGreaterThanOrEqualTo(CompositionSpec.MIN_STEP_SECONDS);

		var flicker = Generator.generate(new Flicker(Flicker.Character.FIRE, 1, 10, 1), FOUR, false,
				FixtureAttributes.ALL);
		assertThat(flicker.fadeSeconds()).isEqualTo(CompositionSpec.MIN_STEP_SECONDS);
	}

	@Test
	void stepOverridesWinOverTheDefaults() {
		var generated = new GeneratedSteps(List.of(
				new GeneratedSteps.Step(java.util.Map.of(1L, FixtureValues.EMPTY)),
				new GeneratedSteps.Step(java.util.Map.of(1L, FixtureValues.EMPTY), Optional.of(0.0), Optional.of(5.0))),
				1, 2, PlayMode.LOOP, IntensityMode.ABSOLUTE);

		var steps = generated.sequence().steps();
		assertThat(steps.get(0).fadeSeconds()).isEqualTo(1);
		assertThat(steps.get(0).holdSeconds()).isEqualTo(2);
		assertThat(steps.get(1).fadeSeconds()).isZero();
		assertThat(steps.get(1).holdSeconds()).isEqualTo(5);
	}

	private static double dimmer(FixtureValues values) {
		return values.get(DIMMER).orElseThrow();
	}
}
