package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.FadeCurve;
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
	void intensityGoesStraightToTheNewLevelDownWithTheFadeOutUpWithTheFadeIn() {
		var down = mix(Map.of(1L, values(DIMMER, 1.0)), Map.of(1L, values(DIMMER, 0.5)), 1000);
		// 0.5 + (1.0 - 0.5) × (1 - 1/4)
		assertThat(dimmer(down, 1)).isCloseTo(0.875, within(1e-9));

		var up = mix(Map.of(1L, values(DIMMER, 0.3)), Map.of(1L, values(DIMMER, 1.0)), 1000);
		// 0.3 + (1.0 - 0.3) × 1/2
		assertThat(dimmer(up, 1)).isCloseTo(0.65, within(1e-9));
	}

	@Test
	void aFixtureLitOnBothSidesNeverDips() {
		var delay = Duration.ofSeconds(2);
		var shortOut = Duration.ofSeconds(1);
		for (var curve : FadeCurve.values()) {
			for (var levels : new double[][] {{0.3, 1.0}, {1.0, 0.3}}) {
				var from = Map.of(1L, values(DIMMER, levels[0]));
				var to = Map.of(1L, values(DIMMER, levels[1]));
				var previous = levels[0];
				for (long ms = 0; ms <= 6000; ms += 50) {
					var withDelay = dimmer(Crossfade.mix(from, to, Duration.ofMillis(ms), IN, OUT, delay, curve,
							FadeBehaviors.DEFAULTS, null), 1);
					var shortFadeOut = dimmer(Crossfade.mix(from, to, Duration.ofMillis(ms), IN, shortOut,
							Duration.ZERO, curve, FadeBehaviors.DEFAULTS, null), 1);
					var what = curve + " " + levels[0] + " → " + levels[1] + " at " + ms + " ms";
					assertThat(withDelay).as(what).isBetween(Math.min(levels[0], levels[1]) - 1e-9,
							Math.max(levels[0], levels[1]) + 1e-9);
					assertThat(shortFadeOut).as(what).isBetween(Math.min(levels[0], levels[1]) - 1e-9,
							Math.max(levels[0], levels[1]) + 1e-9);
					assertThat(levels[1] > levels[0] ? withDelay >= previous - 1e-9 : withDelay <= previous + 1e-9)
							.as("only one direction: " + what).isTrue();
					previous = withDelay;
				}
				assertThat(previous).isCloseTo(levels[1], within(1e-9));
			}
		}
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

	@Test
	void curveShapesLevelsAndTheProgressOfColors() {
		var from = Map.of(1L, values(DIMMER, 1.0, RED, 0.0), 2L, values(DIMMER, 1.0));
		var to = Map.of(1L, values(DIMMER, 1.0, RED, 1.0), 3L, values(DIMMER, 1.0));
		var quarterIn = Duration.ofMillis(500);

		var square = Crossfade.mix(from, to, quarterIn, IN, OUT, Duration.ZERO, FadeCurve.SQUARE,
				FadeBehaviors.DEFAULTS, null);
		assertThat(dimmer(square, 3)).as("(1/4)²").isCloseTo(0.0625, within(1e-9));
		assertThat(dimmer(square, 2)).as("(1 - 1/8)²").isCloseTo(0.765625, within(1e-9));
		assertThat(square.get(1L).get(RED)).as("colors move linearly").contains(0.25);

		var smooth = Crossfade.mix(from, to, quarterIn, IN, OUT, Duration.ZERO, FadeCurve.S_CURVE,
				FadeBehaviors.DEFAULTS, null);
		assertThat(dimmer(smooth, 3)).as("smoothstep(1/4)").isCloseTo(0.15625, within(1e-9));
		assertThat(smooth.get(1L).get(RED).orElseThrow()).isCloseTo(0.15625, within(1e-9));
	}

	@Test
	void curvesStartAtZeroAndEndAtOne() {
		for (var curve : FadeCurve.values()) {
			assertThat(curve.level(0)).isZero();
			assertThat(curve.level(1)).isEqualTo(1.0);
			assertThat(curve.progress(0)).isZero();
			assertThat(curve.progress(1)).isEqualTo(1.0);
			assertThat(FadeCurve.parse(curve.id())).isEqualTo(curve);
		}
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
