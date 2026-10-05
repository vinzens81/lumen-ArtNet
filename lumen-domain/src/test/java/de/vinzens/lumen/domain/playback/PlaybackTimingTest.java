package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Fade times of single scenes: fade-in/fade-out overrides and the delay. Project times: in 2 s, out 4 s. */
class PlaybackTimingTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;
	private static final SceneTiming PROJECT = SceneTiming.DEFAULT;

	private Instant now = Instant.parse("2026-10-05T20:00:00Z");
	private Playback playback;

	@Test
	void flashSceneSwitchesAtOnceAndTheNextSceneFadesWithItsOwnTimes() {
		var dawn = scene(1, PROJECT, Optional.empty());
		var flash = scene(2, timing(0, 0, 0), Optional.empty());
		var night = scene(3, PROJECT, Optional.empty());
		play(dawn, flash, night);
		go();
		advance(5000);
		playback.tick(now, B);

		go();
		assertThat(dimmer(1)).as("old look is gone at once").isZero();
		assertThat(dimmer(2)).isEqualTo(1.0);
		playback.tick(now, B);
		assertThat(playback.fade(now)).isEmpty();

		go();
		advance(1000);
		assertThat(dimmer(2)).as("project fade-out of 4 s").isCloseTo(0.75, within(1e-9));
		assertThat(dimmer(3)).as("project fade-in of 2 s").isCloseTo(0.5, within(1e-9));
	}

	@Test
	void delayHoldsBackTheFadeInWhileTheFadeOutStartsAtGo() {
		var dawn = scene(1, PROJECT, Optional.empty());
		var storm = scene(2, timing(2000, 4000, 1000), Optional.of(Duration.ofSeconds(10)));
		play(dawn, storm, scene(3, PROJECT, Optional.empty()));
		go();
		advance(5000);
		playback.tick(now, B);

		go();
		advance(500);
		assertThat(dimmer(1)).isCloseTo(0.875, within(1e-9));
		assertThat(dimmer(2)).as("still waiting for the delay").isZero();
		assertThat(playback.fade(now)).get().satisfies(fade -> {
			assertThat(fade.delay()).isEqualTo(Duration.ofSeconds(1));
			assertThat(fade.fadeIn()).isEqualTo(Duration.ofSeconds(2));
		});

		advance(1500);
		assertThat(dimmer(2)).isCloseTo(0.5, within(1e-9));
		assertThat(playback.autoFollowRemaining(now)).as("stay starts after delay + fade-in")
				.contains(Duration.ofSeconds(11));
	}

	@Test
	void fadeLastsUntilTheDelayedFadeInIsComplete() {
		var dawn = scene(1, PROJECT, Optional.empty());
		var storm = scene(2, timing(2000, 1000, 3000), Optional.empty());
		play(dawn, storm);
		go();
		advance(5000);
		playback.tick(now, B);

		go();
		advance(4900);
		playback.tick(now, B);
		assertThat(playback.fade(now)).isPresent();
		assertThat(dimmer(1)).isZero();

		advance(100);
		playback.tick(now, B);
		assertThat(playback.fade(now)).isEmpty();
		assertThat(dimmer(2)).isEqualTo(1.0);
	}

	@Test
	void fadeToBlackAtTheEndUsesTheProjectTimes() {
		var last = scene(1, timing(0, 0, 0), Optional.empty());
		play(last);
		go();

		go();
		advance(2000);
		assertThat(playback.nextIsEnd()).isFalse();
		assertThat(dimmer(1)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void timesAreFixedAtGoEvenIfTheSceneIsEditedDuringTheFade() {
		var dawn = scene(1, PROJECT, Optional.empty());
		play(dawn);
		go();
		advance(1000);

		var edited = new Scene(1, 1, new SceneSpec(dawn.spec().number(), "Scene 1", Optional.empty(),
				Optional.empty(), false, timing(0, 0, 0)), dawn.values());
		playback.reload(List.of(edited), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4), now);

		assertThat(dimmer(1)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void sceneCurveOverridesTheProjectCurve() {
		var square = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ZERO, Optional.of(FadeCurve.SQUARE));
		play(scene(1, square, Optional.empty()), scene(2, PROJECT, Optional.empty()));
		playback.useFadeCurve(FadeCurve.S_CURVE);

		go();
		advance(1000);
		assertThat(dimmer(1)).as("square: (1/2)²").isCloseTo(0.25, within(1e-9));
		advance(4000);

		go();
		advance(1000);
		// project S-curve: fade-in 1/2 → 0.5; fade-out 1 - 1/4 = 3/4 → 0.84375
		assertThat(dimmer(2)).isCloseTo(0.5, within(1e-9));
		assertThat(dimmer(1)).isCloseTo(0.84375, within(1e-9));
	}

	private void play(Scene... scenes) {
		playback = new Playback(List.of(scenes), Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	private void go() {
		playback.go(now, B);
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}

	private double dimmer(long fixtureId) {
		return playback.output(now, B).getOrDefault(fixtureId, FixtureValues.EMPTY).get(DIMMER).orElse(0.0);
	}

	private static SceneTiming timing(long fadeInMs, long fadeOutMs, long delayMs) {
		return new SceneTiming(Optional.of(Duration.ofMillis(fadeInMs)), Optional.of(Duration.ofMillis(fadeOutMs)),
				Duration.ofMillis(delayMs));
	}

	/** Scene n lights fixture n. */
	private static Scene scene(long id, SceneTiming timing, Optional<Duration> autoFollow) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.of((int) id), "Scene " + id, Optional.empty(), autoFollow,
				false, timing), Map.of(id, new FixtureValues(Map.of(DIMMER, 1.0))));
	}
}
