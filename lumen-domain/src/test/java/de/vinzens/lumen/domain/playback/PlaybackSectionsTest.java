package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.ScriptMarkers;
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
		playback.finish(START);

		assertThat(playback.goToSection(1, START, B)).isTrue();
		var halfway = playback.output(START.plusSeconds(1), B);
		assertThat(halfway.get(5L).get(DIMMER)).as("in both parts: no movement").contains(1.0);
		assertThat(halfway.get(6L).get(DIMMER).orElseThrow()).as("fading in").isCloseTo(0.5, within(0.01));
		assertThat(playback.section()).isEqualTo(1);

		playback.goToSection(2, START.plusSeconds(3), B);
		assertThat(playback.output(START.plusSeconds(6), B).values())
				.as("a part without light is dark").allSatisfy(values -> assertThat(values.get(DIMMER)).contains(0.0));
		assertThat(playback.goToSection(3, START.plusSeconds(6), B)).as("no such marker").isFalse();
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

	private static ActiveComposition fixed(long id, long fixtureId, double dimmer) {
		var look = new StepSequence(List.of(new StepSequence.Step(1, 0,
				Map.of(fixtureId, new FixtureValues(Map.of(DIMMER, dimmer))))), StepSequence.PlayMode.LOOP);
		return new ActiveComposition(id, Optional.of(id), look, List.of(fixtureId), 1, 1, false);
	}
}
