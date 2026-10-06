package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Chase.Direction;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.Wave;
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

/**
 * Compositions in the playback: running with their scene, fading out while running, continuing into the next
 * scene and freezing with pause.
 */
class PlaybackCompositionTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;
	/** 1 s per step over fixtures 1–4. */
	private static final Chase CHASE = new Chase(1, 1, Direction.FORWARD, 0, 0, 1);

	private Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-10-02T20:00:00Z");
	}

	@Test
	void compositionStartsAtGoAndFadesInWithItsScene() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false))), 2, 2);

		playback.go(now, B);
		assertThat(dimmer(playback, 1)).as("starts dark with the fade").isEqualTo(0.0);
		advance(1000); // head on fixture 2, fade-in 50 %
		assertThat(dimmer(playback, 2)).isCloseTo(0.5, within(1e-9));
		assertThat(dimmer(playback, 1)).isEqualTo(0.0);
		advance(1500);
		playback.tick(now, B);
		assertThat(dimmer(playback, 3)).isEqualTo(1.0);
	}

	@Test
	void pauseFreezesTheComposition() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false))), 0, 0);
		playback.go(now, B);
		advance(1200);
		playback.togglePause(now);
		advance(5000);
		assertThat(dimmer(playback, 2)).as("still on step 2").isEqualTo(1.0);
		playback.togglePause(now);
		advance(900);
		assertThat(dimmer(playback, 3)).isEqualTo(1.0);
	}

	@Test
	void outgoingCompositionKeepsRunningWhileItFadesOut() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false))), 4, 4);
		playback.go(now, B);
		advance(4000); // fade-in done, head on fixture 1 (step 4)
		playback.tick(now, B);

		playback.go(now, B); // to scene 2 without compositions
		advance(1000); // head moves on to fixture 2, fade-out at 75 %
		assertThat(dimmer(playback, 2)).isCloseTo(0.75, within(1e-9));
		assertThat(dimmer(playback, 1)).isEqualTo(0.0);
	}

	@Test
	void copyOfTheSameOriginContinuesWithoutPhaseJumpOrDip() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false)), 2L, List.of(chase(21, 100L, false))), 4,
				4);
		playback.go(now, B);
		advance(4000);
		playback.tick(now, B);

		playback.go(now, B);
		for (int ms = 0; ms <= 4000; ms += 250) {
			var step = (int) ((4000 + ms) / 1000) % 4; // phase continues from scene 1
			for (long fixture = 1; fixture <= 4; fixture++) {
				assertThat(dimmer(playback, fixture)).as("t=%d ms fixture %d", ms, fixture)
						.isEqualTo(fixture - 1 == step ? 1.0 : 0.0);
			}
			advance(250);
		}
	}

	@Test
	void restartStartsAgainAndFadesIn() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false)), 2L, List.of(chase(21, 100L, true))), 2,
				2);
		playback.go(now, B);
		advance(2500);
		playback.tick(now, B);

		playback.go(now, B);
		advance(1000); // restarted chase on step 1 (fixture 2), fade-in 50 %; old one on step 3 (fixture 4) fading
		assertThat(dimmer(playback, 2)).isCloseTo(0.5, within(1e-9));
		assertThat(dimmer(playback, 4)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void continuingCompositionGlidesToItsNewLevel() {
		var playback = playback(Map.of(
				1L, List.of(active(11, 100L, wave(), 1.0, false)),
				2L, List.of(active(21, 100L, wave(), 0.5, false))), 2, 2);
		playback.go(now, B);
		advance(2000); // fade-in done; sawtooth with 4 s period: 0.5
		playback.tick(now, B);
		assertThat(dimmer(playback, 1)).isCloseTo(0.5, within(1e-9));

		playback.go(now, B);
		assertThat(dimmer(playback, 1)).as("no jump at GO").isCloseTo(0.5, within(1e-9));
		advance(1000); // phase 0.75; level 1 → 0.5 at 50 % = 0.75
		assertThat(dimmer(playback, 1)).isCloseTo(0.75 * 0.75, within(1e-9));
		advance(1000); // fade done: phase 0 again, level 0.5
		playback.tick(now, B);
		advance(500);
		assertThat(dimmer(playback, 1)).isCloseTo(0.5 * 0.125, within(1e-9));
	}

	@Test
	void doubleGoDoesNotJump() {
		var playback = playback(Map.of(1L, List.of(chase(11, 100L, false))), 2, 2);
		playback.go(now, B);
		advance(1000);
		var before = playback.output(now, B);

		playback.go(now, B);

		assertThat(playback.output(now, B)).isEqualTo(before);
	}

	@Test
	void editingTheSceneKeepsThePhase() {
		var compositions = Map.of(1L, List.of(chase(11, 100L, false)));
		var playback = playback(compositions, 0, 0);
		playback.go(now, B);
		advance(2500);

		var faster = Map.of(1L, List.of(new ActiveComposition(11, Optional.of(100L), CHASE, List.of(1L, 2L, 3L, 4L),
				1, 1, false), chase(12, 101L, false)));
		playback.reload(scenes(), faster, Duration.ZERO, Duration.ZERO, now);

		assertThat(dimmer(playback, 3)).as("chase 11 keeps step 2").isEqualTo(1.0);
		assertThat(dimmer(playback, 1)).as("new chase 12 starts now").isEqualTo(1.0);
	}

	private Playback playback(Map<Long, List<ActiveComposition>> compositions, int fadeInSeconds,
			int fadeOutSeconds) {
		return new Playback(scenes(), compositions, Duration.ofSeconds(fadeInSeconds),
				Duration.ofSeconds(fadeOutSeconds));
	}

	private static List<Scene> scenes() {
		return List.of(scene(1), scene(2));
	}

	private static Scene scene(long id) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.of((int) id), "Scene " + id, Optional.empty(),
				Optional.empty()));
	}

	private static ActiveComposition chase(long id, long source, boolean restart) {
		return active(id, source, CHASE, 1, restart);
	}

	private static Pattern wave() {
		return new Wave(Wave.Shape.SAWTOOTH, 4, 0, 1, 0);
	}

	private static ActiveComposition active(long id, long source, Pattern pattern, double level, boolean restart) {
		var fixtures = pattern instanceof Chase ? List.of(1L, 2L, 3L, 4L) : List.of(1L);
		return new ActiveComposition(id, Optional.of(source), pattern, fixtures, level, 1, restart);
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}

	private double dimmer(Playback playback, long fixtureId) {
		return playback.output(now, B).getOrDefault(fixtureId, FixtureValues.EMPTY).get(DIMMER).orElse(0.0);
	}
}
