package de.vinzens.lumen.domain.playback;

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

class PlaybackTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;

	private final Scene dawn = scene(1, "1", 1L, Optional.empty());
	private final Scene storm = scene(2, "2", 2L, Optional.of(Duration.ofSeconds(5)));
	private final Scene night = scene(3, "3", 3L, Optional.empty());

	private Instant now;
	private Playback playback;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-10-02T20:00:00Z");
		playback = new Playback(List.of(dawn, storm, night), Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	@Test
	void startsDarkAndGoesThroughTheShow() {
		assertThat(playback.position()).isEqualTo(Playback.READY);
		assertThat(playback.output(now, B)).isEmpty();
		assertThat(playback.nextScene()).contains(dawn);

		go();
		assertThat(playback.currentScene()).contains(dawn);
		advance(1000);
		assertThat(dimmer(1)).isCloseTo(0.5, within(1e-9));
		advance(3000);
		playback.tick(now, B);
		assertThat(playback.fade(now)).isEmpty();
		assertThat(dimmer(1)).isEqualTo(1.0);
	}

	@Test
	void goAfterTheLastSceneFadesToBlackAndThenDoesNothing() {
		go();
		go();
		go();
		assertThat(playback.nextIsEnd()).isTrue();

		go();
		assertThat(playback.position()).isEqualTo(3);
		advance(4000);
		assertThat(playback.output(now, B).values()).allSatisfy(v -> assertThat(v.get(DIMMER)).contains(0.0));
		assertThat(playback.go(now, B)).isFalse();
	}

	@Test
	void doubleGoDuringAFadeContinuesFromWhatIsOnStage() {
		go();
		advance(1000); // dawn at 50 %
		go(); // storm: snapshot holds dawn at 50 %

		assertThat(dimmer(1)).as("no jump at GO").isCloseTo(0.5, within(1e-9));
		advance(2000); // snapshot fades out: 0.5 × (1 - 2/4)
		assertThat(dimmer(1)).isCloseTo(0.25, within(1e-9));
		assertThat(dimmer(2)).isEqualTo(1.0);
	}

	@Test
	void backReturnsToThePreviousScene() {
		go();
		go();
		playback.back(now, B);

		assertThat(playback.currentScene()).contains(dawn);
		playback.back(now, B);
		assertThat(playback.position()).isEqualTo(Playback.READY);
		assertThat(playback.back(now, B)).isFalse();
	}

	@Test
	void jumpChoosesTheNextScene() {
		go();
		playback.setNext(night.id());

		assertThat(playback.nextScene()).contains(night);
		go();
		assertThat(playback.currentScene()).contains(night);
		assertThat(playback.nextIsEnd()).isTrue();
	}

	@Test
	void pauseFreezesTheFade() {
		go();
		advance(1000);
		playback.togglePause(now);
		advance(10_000);
		assertThat(dimmer(1)).isCloseTo(0.5, within(1e-9));

		playback.togglePause(now);
		advance(500);
		assertThat(dimmer(1)).isCloseTo(0.75, within(1e-9));
	}

	@Test
	void finishCompletesTheFadeAtOnce() {
		go();
		advance(100);
		playback.finish(now);

		assertThat(playback.fade(now)).isEmpty();
		assertThat(dimmer(1)).isEqualTo(1.0);
	}

	@Test
	void autoFollowStartsAfterTheFadeInAndHonorsPause() {
		go();
		go(); // storm: auto-follow 5 s after its 2 s fade-in
		advance(6000);
		assertThat(playback.tick(now, B)).isTrue(); // fade finished
		assertThat(playback.autoFollowRemaining(now)).contains(Duration.ofSeconds(1));

		playback.togglePause(now);
		advance(5000);
		playback.tick(now, B);
		assertThat(playback.currentScene()).as("paused").contains(storm);

		playback.togglePause(now);
		advance(1000);
		assertThat(playback.tick(now, B)).isTrue();
		assertThat(playback.currentScene()).contains(night);
	}

	@Test
	void autoFollowOnTheLastSceneIsIgnored() {
		var last = scene(9, "9", 1L, Optional.of(Duration.ofSeconds(1)));
		playback = new Playback(List.of(last), Duration.ZERO, Duration.ZERO);
		go();
		assertThat(playback.tick(now, B)).as("zero fade ends at once").isTrue();
		advance(5000);

		assertThat(playback.tick(now, B)).isFalse();
		assertThat(playback.currentScene()).contains(last);
	}

	@Test
	void reloadKeepsTheCurrentSceneById() {
		go();
		go();
		var inserted = scene(4, "1.5", 1L, Optional.empty());

		playback.reload(List.of(dawn, inserted, storm, night), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4),
				now);

		assertThat(playback.currentScene()).contains(storm);
		assertThat(playback.position()).isEqualTo(2);
	}

	@Test
	void sceneAddedAfterTheEndOfTheShowDoesNotComeOnStage() {
		go();
		go();
		go();
		go();
		advance(10_000);
		playback.tick(now, B);
		var encore = scene(4, "4", 1L, Optional.empty());

		playback.reload(List.of(dawn, storm, night, encore), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4),
				now);

		assertThat(playback.currentScene()).isEmpty();
		assertThat(playback.position()).isEqualTo(4);
		assertThat(dimmer(1)).isEqualTo(0.0);
	}

	@Test
	void sceneAddedDuringTheFadeToTheEndDoesNotComeOnStage() {
		go();
		go();
		go();
		go();
		advance(1000);
		var encore = scene(4, "4", 4L, Optional.empty());

		playback.reload(List.of(dawn, storm, night, encore), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4),
				now);

		assertThat(playback.currentScene()).isEmpty();
		assertThat(dimmer(4)).isEqualTo(0.0);
	}

	@Test
	void deletedSceneStaysOnStageUntilTheNextGo() {
		go();
		advance(5000);
		playback.tick(now, B);

		playback.reload(List.of(storm, night), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4), now);

		assertThat(dimmer(1)).as("no jump").isEqualTo(1.0);
		assertThat(playback.nextScene()).contains(storm);

		go();
		advance(1000);
		assertThat(dimmer(1)).as("fades out like any scene").isCloseTo(0.75, within(1e-9));
		advance(4000);
		playback.tick(now, B);
		assertThat(playback.currentScene()).contains(storm);
		assertThat(playback.scenes()).as("deleted scene is gone once it left the stage").containsExactly(storm, night);
		assertThat(playback.position()).isEqualTo(0);
	}

	@Test
	void deletedSceneMakesWayForANewSceneWithTheSameNumber() {
		go();
		go();
		var replacement = scene(4, "2", 4L, Optional.empty());

		playback.reload(List.of(dawn, replacement, night), Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(4), now);

		assertThat(playback.currentScene()).contains(storm);
		assertThat(playback.nextScene()).contains(replacement);
	}

	@Test
	void restorePutsASceneOnStageWithoutFade() {
		go();
		advance(500);

		playback.restore(storm.id(), now);

		assertThat(playback.currentScene()).contains(storm);
		assertThat(playback.fade(now)).isEmpty();
		assertThat(dimmer(1)).isEqualTo(0.0);
		assertThat(dimmer(2)).isEqualTo(1.0);
		assertThat(playback.nextScene()).contains(night);
		assertThat(playback.autoFollowRemaining(now)).as("countdown starts now").contains(Duration.ofSeconds(5));
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

	private static Scene scene(long id, String number, long fixtureId, Optional<Duration> autoFollow) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.parse(number), "Scene " + number, Optional.empty(),
				autoFollow), Map.of(fixtureId, new FixtureValues(Map.of(DIMMER, 1.0))));
	}
}
