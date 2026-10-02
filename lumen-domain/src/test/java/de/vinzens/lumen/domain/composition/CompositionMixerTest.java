package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.composition.Chase.Direction;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CompositionMixerTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute BLUE = Attribute.of(ChannelFunction.BLUE);

	@Test
	void absoluteIntensityIsHighestTakesPrecedenceWithTheBase() {
		var chase = active(new Chase(1, 1, Direction.FORWARD, 0, 0, 1), 0.8, 1L, 2L);
		var base = Map.of(1L, values(Map.of(DIMMER, 0.3)), 2L, values(Map.of(DIMMER, 0.3)));

		var look = CompositionMixer.apply(base, chase, 0, FixtureAttributes.ALL);

		assertThat(look.get(1L).get(DIMMER)).contains(0.8);
		assertThat(look.get(2L).get(DIMMER)).as("base stays").contains(0.3);
	}

	@Test
	void relativeIntensityScalesTheBase() {
		var flicker = active(new Flicker(Flicker.Character.CANDLE, 1, 1, 7), 0.5, 1L);
		var base = Map.of(1L, values(Map.of(DIMMER, 0.8)));
		var factor = flicker.pattern().valuesAt(1.3, 0, 1, 1).get(DIMMER).orElseThrow();

		var look = CompositionMixer.apply(base, flicker, 1.3, FixtureAttributes.ALL);

		assertThat(look.get(1L).get(DIMMER).orElseThrow()).isCloseTo(0.8 * (0.5 + factor * 0.5), within(1e-9));
	}

	@Test
	void colorIsBlendedOverTheBaseByLevel() {
		var cycle = active(new ColorCycle(List.of(new Color(0, 0, 1), new Color(0, 0, 1)), 1, 0, 0, 1), 0.5, 1L);
		var base = Map.of(1L, values(Map.of(DIMMER, 0.6, RED, 1.0, BLUE, 0.0)));

		var look = CompositionMixer.apply(base, cycle, 0, FixtureAttributes.ALL);

		assertThat(look.get(1L).get(RED)).contains(0.5);
		assertThat(look.get(1L).get(BLUE)).contains(0.5);
		assertThat(look.get(1L).get(DIMMER)).as("base intensity kept").contains(0.6);
	}

	@Test
	void colorWithoutBaseIntensityUsesTheFallbackAndOnlySupportedAttributes() {
		var cycle = active(new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0, 1)), 1, 0, 0, 0.9), 0.5, 1L, 2L);
		FixtureAttributes onlyDimmerOn2 = (fixtureId, attribute) -> fixtureId == 1 || attribute.equals(DIMMER);

		var look = CompositionMixer.apply(Map.of(), cycle, 0, onlyDimmerOn2);

		assertThat(look.get(1L).get(RED)).contains(1.0);
		assertThat(look.get(1L).get(DIMMER).orElseThrow()).isCloseTo(0.45, within(1e-9));
		assertThat(look.get(2L).values()).as("a dimmer cannot show a color").isEmpty();
	}

	@Test
	void speedFactorScalesTime() {
		var chase = new ActiveComposition(1, Optional.empty(), new Chase(1, 1, Direction.FORWARD, 0, 0, 1),
				List.of(1L, 2L), 1, 2, false);
		var look = CompositionMixer.apply(Map.of(), chase, 0.5, FixtureAttributes.ALL);
		assertThat(look.get(2L).get(DIMMER)).as("0.5 s × 2 = step 1").contains(1.0);
	}

	@Test
	void runOnceStandsStillAtTheEnd() {
		var chase = once(new Chase(1, 1, Direction.FORWARD, 0, 0, 1), 1L, 2L, 3L, 4L);
		assertThat(CompositionMixer.apply(Map.of(), chase, 2.5, FixtureAttributes.ALL).get(3L).get(DIMMER))
				.as("still running").contains(1.0);
		var end = CompositionMixer.apply(Map.of(), chase, 100, FixtureAttributes.ALL);
		assertThat(end.get(4L).get(DIMMER)).as("stays on the last fixture").contains(1.0);
		assertThat(end.get(1L).get(DIMMER)).contains(0.0);

		var sunrise = once(new ColorCycle(List.of(new Color(1, 0, 0), new Color(1, 0.8, 0.4)), 0, 30, 0, 1), 1L);
		assertThat(CompositionMixer.apply(Map.of(), sunrise, 15, FixtureAttributes.ALL).get(1L).get(BLUE).orElseThrow())
				.isCloseTo(0.2, within(1e-9));
		assertThat(CompositionMixer.apply(Map.of(), sunrise, 600, FixtureAttributes.ALL).get(1L).get(BLUE))
				.as("stays at the last color").contains(0.4);

		var ramp = once(new Wave(Wave.Shape.SAWTOOTH, 10, 0, 1, 0), 1L);
		assertThat(CompositionMixer.apply(Map.of(), ramp, 60, FixtureAttributes.ALL).get(1L).get(DIMMER).orElseThrow())
				.as("a sawtooth ends at its high").isCloseTo(1.0, within(1e-6));

		var flicker = once(new Flicker(Flicker.Character.CANDLE, 1, 1, 3), 1L);
		assertThat(flicker.patternSeconds(500)).as("flicker has no end").isEqualTo(500);
	}

	private static ActiveComposition once(Pattern pattern, Long... fixtures) {
		return new ActiveComposition(1, Optional.empty(), pattern, List.of(fixtures), 1, 1, false, true);
	}

	private static ActiveComposition active(Pattern pattern, double level, Long... fixtures) {
		return new ActiveComposition(1, Optional.empty(), pattern, List.of(fixtures), level, 1, false);
	}

	private static FixtureValues values(Map<Attribute, Double> values) {
		return new FixtureValues(values);
	}
}
