package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSection;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import de.vinzens.lumen.domain.scene.ScriptMarkers;
import de.vinzens.lumen.domain.scene.SectionTiming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlaybackSectionsTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;
	private static final Instant START = Instant.parse("2026-10-06T19:00:00Z");

	private static final ActiveComposition WARM = fixed(1, 5L, 1.0);
	private static final ActiveComposition SPOT = fixed(2, 6L, 1.0);

	@Test
	void markersAreFoundInTheScriptWithTheirInfo() {
		var markers = ScriptMarkers.parse("""
				**Enter Michael**
				Michael: Hello.
				//## Licht Marker 1 (infotext 1)##
				Doro: Hi.
				// ## Window ##
				not // ## a marker""");

		assertThat(markers).containsExactly(new ScriptMarkers.Marker("Licht Marker 1", "infotext 1"),
				new ScriptMarkers.Marker("Window", ""));
	}

	@Test
	void aMarkerFadesToThePartsLightAndWhatStaysDoesNotMove() {
		var scene = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty()));
		var playback = new Playback(List.of(scene), Map.of(1L, List.of(WARM)), Duration.ofSeconds(2),
				Duration.ofSeconds(2));
		playback.useSections(Map.of(1L, List.of(List.of(WARM, SPOT), List.of())));
		playback.go(START, B);
		assertThat(playback.fade(START)).as("a GO into another scene")
				.hasValueSatisfying(fade -> assertThat(fade.withinScene()).isFalse());
		playback.finish(START);

		assertThat(playback.goToSection(1, START, B)).isTrue();
		var halfway = playback.output(START.plusSeconds(1), B);
		assertThat(halfway.get(5L).get(DIMMER)).as("in both parts: no movement").contains(1.0);
		assertThat(halfway.get(6L).get(DIMMER).orElseThrow()).as("fading in").isCloseTo(0.5, within(0.01));
		assertThat(playback.section()).isEqualTo(1);
		assertThat(playback.fade(START)).hasValueSatisfying(fade -> assertThat(fade.withinScene()).isTrue());

		playback.goToSection(2, START.plusSeconds(3), B);
		assertThat(playback.output(START.plusSeconds(6), B).values())
				.as("a part without light is dark").allSatisfy(values -> assertThat(values.get(DIMMER)).contains(0.0));
		assertThat(playback.goToSection(3, START.plusSeconds(6), B)).as("no such marker").isFalse();
	}

	@Test
	void aMarkerFadesWithoutTheDelayOfTheScene() {
		var timing = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ofSeconds(1));
		var scene = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty(),
				timing));
		var playback = new Playback(List.of(scene), Map.of(1L, List.of(WARM)), Duration.ofSeconds(2),
				Duration.ofSeconds(2));
		playback.useSections(Map.of(1L, List.of(List.of(SPOT))));
		playback.go(START, B);
		assertThat(playback.output(START.plusMillis(500), B).get(5L).get(DIMMER)).as("the GO waits for the delay")
				.contains(0.0);
		playback.finish(START);

		playback.goToSection(1, START, B);
		assertThat(playback.output(START.plusSeconds(1), B).get(6L).get(DIMMER).orElseThrow())
				.as("the marker fades at once").isCloseTo(0.5, within(0.01));
		assertThat(playback.fade(START)).hasValueSatisfying(fade -> assertThat(fade.delay()).isZero());
	}

	@Test
	void aMarkerFadesWithTheOwnTimesOfItsPart() {
		var spec = new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty(), SceneTiming.DEFAULT,
				"Romeo: Soft!\n//## Window ##\nJuliet: Ay me!");
		var window = new SceneSection("Window", List.of(), List.of(), List.of(),
				new SectionTiming(Optional.of(Duration.ofSeconds(4)), Optional.of(Duration.ofSeconds(1))));
		var scene = new Scene(1, 1, spec, List.of(), List.of(), List.of(), List.of(), List.of(window));
		var playback = new Playback(List.of(scene), Map.of(1L, List.of(WARM)), Duration.ofSeconds(2),
				Duration.ofSeconds(2));
		playback.useSections(Map.of(1L, List.of(List.of(SPOT))));
		playback.go(START, B);
		playback.finish(START);

		playback.goToSection(1, START, B);

		var halfway = playback.output(START.plusSeconds(2), B);
		assertThat(halfway.get(6L).get(DIMMER).orElseThrow()).as("4 s fade-in of the part").isCloseTo(0.5,
				within(0.01));
		assertThat(halfway.get(5L).get(DIMMER)).as("1 s fade-out of the part").contains(0.0);
		assertThat(playback.fade(START)).hasValueSatisfying(fade -> assertThat(fade.fadeIn())
				.isEqualTo(Duration.ofSeconds(4)));
	}

	@Test
	void goToFadesFromLightOutsideTheCueListIntoAPartOfAnyScene() {
		var timing = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ofSeconds(1));
		var spec = new SceneSpec(SceneNumber.of(2), "Two", Optional.empty(), Optional.empty(), timing,
				"Romeo: Soft!\n//## Window ##\nJuliet: Ay me!");
		var window = new SceneSection("Window", List.of(), List.of(), List.of(),
				new SectionTiming(Optional.of(Duration.ofSeconds(4)), Optional.empty()));
		var one = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty()));
		var two = new Scene(2, 1, spec, List.of(), List.of(), List.of(), List.of(), List.of(window));
		var playback = new Playback(List.of(one, two), Map.of(1L, List.of(SPOT), 2L, List.of(SPOT)),
				Duration.ofSeconds(2), Duration.ofSeconds(2));
		playback.useSections(Map.of(2L, List.of(List.of(WARM, SPOT))));
		playback.restoreLight(List.of(WARM), START);

		assertThat(playback.output(START, B).get(5L).get(DIMMER)).as("the light is on stage").contains(1.0);
		assertThat(playback.currentScene()).isEmpty();

		playback.goTo(2, 1, START, B);

		var halfway = playback.output(START.plusSeconds(2), B);
		assertThat(halfway.get(5L).get(DIMMER)).as("in both: does not move").contains(1.0);
		assertThat(halfway.get(6L).get(DIMMER).orElseThrow()).as("4 s of the part, without the scene's delay")
				.isCloseTo(0.5, within(0.01));
		assertThat(playback.section()).isEqualTo(1);
		assertThat(playback.currentScene()).contains(two);

		playback.finish(START.plusSeconds(2));
		playback.goTo(2, 0, START.plusSeconds(2), B);
		assertThat(playback.fade(START.plusSeconds(2))).as("into the start: the times of the GO")
				.hasValueSatisfying(fade -> assertThat(fade.delay()).isEqualTo(Duration.ofSeconds(1)));
	}

	@Test
	void restorePutsAPartOnStageWithoutFade() {
		var scene = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty()));
		var playback = new Playback(List.of(scene), Map.of(1L, List.of(WARM)), Duration.ofSeconds(2),
				Duration.ofSeconds(2));
		playback.useSections(Map.of(1L, List.of(List.of(SPOT))));

		playback.restore(1L, 1, START);

		assertThat(playback.section()).isEqualTo(1);
		assertThat(playback.fade(START)).isEmpty();
		assertThat(playback.runningCompositions()).containsExactly(SPOT);
		playback.restore(1L, 2, START);
		assertThat(playback.section()).as("no such marker: the start").isZero();
	}

	@Test
	void theNextSceneStartsAtItsBeginning() {
		var one = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty()));
		var two = new Scene(2, 1, new SceneSpec(SceneNumber.of(2), "Two", Optional.empty(), Optional.empty()));
		var playback = new Playback(List.of(one, two), Map.of(1L, List.of(WARM), 2L, List.of(SPOT)),
				Duration.ZERO, Duration.ZERO);
		playback.useSections(Map.of(1L, List.of(List.of(SPOT)), 2L, List.of(List.of(WARM))));
		playback.go(START, B);
		playback.goToSection(1, START, B);

		playback.go(START.plusSeconds(1), B);

		assertThat(playback.section()).isZero();
		assertThat(playback.runningCompositions()).containsExactly(SPOT);
	}

	@Test
	void stopMakesEverythingDarkAtOnceAndStartsAgainFromTheFirstScene() {
		var one = new Scene(1, 1, new SceneSpec(SceneNumber.of(1), "One", Optional.empty(), Optional.empty()));
		var two = new Scene(2, 1, new SceneSpec(SceneNumber.of(2), "Two", Optional.empty(), Optional.empty()));
		var playback = new Playback(List.of(one, two), Map.of(1L, List.of(WARM), 2L, List.of(SPOT)),
				Duration.ofSeconds(2), Duration.ofSeconds(2));
		playback.go(START, B);
		playback.go(START.plusSeconds(5), B);
		playback.togglePause(START.plusSeconds(6));

		playback.stop(START.plusSeconds(6));

		assertThat(playback.position()).isEqualTo(Playback.READY);
		assertThat(playback.isPaused()).isFalse();
		assertThat(playback.fade(START.plusSeconds(6))).isEmpty();
		assertThat(playback.output(START.plusSeconds(6), B)).as("dark at once").isEmpty();
		playback.go(START.plusSeconds(7), B);
		assertThat(playback.currentScene()).map(Scene::id).contains(1L);
	}

	private static ActiveComposition fixed(long id, long fixtureId, double dimmer) {
		var look = new StepSequence(List.of(new StepSequence.Step(1, 0,
				Map.of(fixtureId, new FixtureValues(Map.of(DIMMER, dimmer))))), StepSequence.PlayMode.LOOP);
		return new ActiveComposition(id, Optional.of(id), look, List.of(fixtureId), 1, 1, false);
	}
}
