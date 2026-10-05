package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CrossfadeTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final Attribute GOBO = Attribute.of(ChannelFunction.GOBO);
	private static final Duration IN = Duration.ofSeconds(2);
	private static final Duration OUT = Duration.ofSeconds(4);

	@Test
	void intensityIsHighestTakesPrecedenceOfBothLevels() {
		var from = Map.of(1L, values(DIMMER, 1.0));
		var to = Map.of(1L, values(DIMMER, 0.5));

		// after 1 s: old 1.0 × (1 - 1/4) = 0.75, new 0.5 × 1/2 = 0.25
		assertThat(dimmer(mix(from, to, 1000), 1)).isCloseTo(0.75, within(1e-9));
		// after 3 s: old 1.0 × 1/4 = 0.25, new 0.5 × 1 = 0.5
		assertThat(dimmer(mix(from, to, 3000), 1)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void fixturesOnlyInOneSceneFadeOutOrIn() {
		var from = Map.of(1L, values(DIMMER, 1.0));
		var to = Map.of(2L, values(DIMMER, 1.0));

		var halfway = mix(from, to, 1000);
		assertThat(dimmer(halfway, 1)).isCloseTo(0.75, within(1e-9));
		assertThat(dimmer(halfway, 2)).isCloseTo(0.5, within(1e-9));

		var done = mix(from, to, 4000);
		assertThat(dimmer(done, 1)).isZero();
		assertThat(dimmer(done, 2)).isEqualTo(1.0);
	}

	@Test
	void colorFadesWithTheFadeInWhenTheFixtureWasLit() {
		var from = Map.of(1L, values(DIMMER, 1.0, RED, 0.0));
		var to = Map.of(1L, values(DIMMER, 1.0, RED, 1.0));

		assertThat(mix(from, to, 500).get(1L).get(RED)).contains(0.25);
		assertThat(mix(from, to, 2000).get(1L).get(RED)).contains(1.0);
	}

	@Test
	void colorJumpsWhenTheFixtureWasDark() {
		var from = Map.of(1L, values(DIMMER, 0.0, RED, 0.0));
		var to = Map.of(1L, values(DIMMER, 1.0, RED, 1.0));

		assertThat(mix(from, to, 1).get(1L).get(RED)).contains(1.0);
	}

	@Test
	void snapAttributesJumpAndUnsetAttributesAreKept() {
		var from = Map.of(1L, values(DIMMER, 1.0, GOBO, 0.2, RED, 0.7));
		var to = Map.of(1L, values(DIMMER, 1.0, GOBO, 0.6));

		var mixed = mix(from, to, 100).get(1L);
		assertThat(mixed.get(GOBO)).contains(0.6);
		assertThat(mixed.get(RED)).as("not set in the new scene").contains(0.7);
	}

	@Test
	void zeroFadeTimesSwitchAtOnce() {
		var mixed = Crossfade.mix(Map.of(1L, values(DIMMER, 1.0)), Map.of(2L, values(DIMMER, 1.0)), Duration.ZERO,
				Duration.ZERO, Duration.ZERO, FadeBehaviors.DEFAULTS);

		assertThat(dimmer(mixed, 1)).isZero();
		assertThat(dimmer(mixed, 2)).isEqualTo(1.0);
	}

	@Test
	void delayHoldsBackTheFadeInAndSnapAttributes() {
		var from = Map.of(1L, values(DIMMER, 1.0, GOBO, 0.2));
		var to = Map.of(1L, values(DIMMER, 0.0, GOBO, 0.6), 2L, values(DIMMER, 1.0));
		var delay = Duration.ofSeconds(1);

		var waiting = Crossfade.mix(from, to, Duration.ofMillis(500), IN, OUT, delay, FadeBehaviors.DEFAULTS, null);
		assertThat(dimmer(waiting, 1)).as("fade-out runs from GO").isCloseTo(0.875, within(1e-9));
		assertThat(dimmer(waiting, 2)).isZero();
		assertThat(waiting.get(1L).get(GOBO)).contains(0.2);

		var fading = Crossfade.mix(from, to, Duration.ofMillis(2000), IN, OUT, delay, FadeBehaviors.DEFAULTS, null);
		assertThat(dimmer(fading, 2)).isCloseTo(0.5, within(1e-9));
		assertThat(fading.get(1L).get(GOBO)).contains(0.6);
	}

	private static Map<Long, FixtureValues> mix(Map<Long, FixtureValues> from, Map<Long, FixtureValues> to, long ms) {
		return Crossfade.mix(from, to, Duration.ofMillis(ms), IN, OUT, FadeBehaviors.DEFAULTS);
	}

	private static double dimmer(Map<Long, FixtureValues> values, long fixtureId) {
		return values.get(fixtureId).get(DIMMER).orElseThrow();
	}

	private static FixtureValues values(Object... pairs) {
		var map = new java.util.HashMap<Attribute, Double>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Attribute) pairs[i], (Double) pairs[i + 1]);
		}
		return new FixtureValues(map);
	}
}
