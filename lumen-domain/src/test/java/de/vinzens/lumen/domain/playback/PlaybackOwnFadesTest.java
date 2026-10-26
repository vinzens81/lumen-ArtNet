package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Compositions with fade times of their own: their fixtures fade with them, the others with the transition. */
class PlaybackOwnFadesTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;

	private Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-10-26T20:00:00Z");
	}

	@Test
	void compositionWithItsOwnFadeInFadesSlowerThanTheRest() {
		var slow = lit(11, 1).withFades(Optional.of(Duration.ofSeconds(4)), Optional.empty());
		var playback = playback(Map.of(1L, List.of(slow, lit(12, 2))));

		playback.go(now, B);
		advance(1000);
		assertThat(dimmer(playback, 1)).isCloseTo(0.25, within(1e-9));
		assertThat(dimmer(playback, 2)).isCloseTo(0.5, within(1e-9));

		advance(1500);
		playback.tick(now, B);
		assertThat(dimmer(playback, 2)).isEqualTo(1.0);
		assertThat(playback.fade(now)).as("the fade lasts as long as the slowest composition").isPresent()
				.get().extracting(Playback.FadeStatus::fadeIn).isEqualTo(Duration.ofSeconds(4));

		advance(1500);
		playback.tick(now, B);
		assertThat(dimmer(playback, 1)).isEqualTo(1.0);
		assertThat(playback.fade(now)).isEmpty();
	}

	@Test
	void compositionWithItsOwnFadeOutLeavesFasterThanTheRest() {
		var snap = lit(11, 1).withFades(Optional.empty(), Optional.of(Duration.ZERO));
		var playback = playback(Map.of(1L, List.of(snap, lit(12, 2))));
		playback.go(now, B);
		advance(2000);
		playback.tick(now, B);

		playback.go(now, B); // to scene 2 without light
		advance(1000);
		assertThat(dimmer(playback, 1)).isEqualTo(0.0);
		assertThat(dimmer(playback, 2)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void ownFadeInWinsOverTheSceneOnASharedFixture() {
		var quick = lit(11, 1).withFades(Optional.of(Duration.ofSeconds(1)), Optional.empty());
		var playback = playback(Map.of(1L, List.of(lit(12, 1), quick)));

		playback.go(now, B);
		advance(1000);
		assertThat(dimmer(playback, 1)).isEqualTo(1.0);
	}

	/** Two scenes, fade-in and fade-out 2 s. */
	private static Playback playback(Map<Long, List<ActiveComposition>> compositions) {
		return new Playback(List.of(scene(1), scene(2)), compositions, Duration.ofSeconds(2), Duration.ofSeconds(2));
	}

	private static Scene scene(long id) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.of((int) id), "Scene " + id, Optional.empty(),
				Optional.empty()));
	}

	/** One fixture at full. */
	private static ActiveComposition lit(long id, long fixtureId) {
		return StaticLooks.of(id, Map.of(fixtureId, new FixtureValues(Map.of(DIMMER, 1.0))));
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}

	private double dimmer(Playback playback, long fixtureId) {
		return playback.output(now, B).getOrDefault(fixtureId, FixtureValues.EMPTY).get(DIMMER).orElse(0.0);
	}
}
