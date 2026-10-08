package de.vinzens.lumen.domain.playback;

import de.vinzens.lumen.domain.composition.ActiveComposition;
import de.vinzens.lumen.domain.composition.MovePattern;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.MoveInDark;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Move in dark: heads that point somewhere else dim out (0.5 s), jump while dark (travel 1.5 s) and dim in with the
 * fade-in (2 s); everything else fades as usual.
 */
class PlaybackMoveInDarkTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;
	private static final Figure LEFT = spot(1, 0.2);
	private static final Figure RIGHT = spot(2, 0.8);
	private static final Figure LINE = new Figure(3, 1, new FigureSpec("Line", true,
			List.of(new FigurePoint(0, 0.5), new FigurePoint(1, 0.5))));

	private Instant now = Instant.parse("2026-10-08T20:00:00Z");
	private Playback playback;

	@Test
	void aLitHeadDimsOutMovesInTheDarkAndDimsIn() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 0.8, Map.of(RED, 1.0)), move(2, RIGHT, 1))), MoveInDark.on());
		go();
		advance(250);
		assertThat(value(1, DIMMER)).as("dimming out").isCloseTo(0.5, within(1e-9));
		assertThat(value(1, MovePattern.PAN)).as("still at the old position").isCloseTo(0.2, within(1e-9));
		advance(500);
		assertThat(value(1, DIMMER)).as("dark while travelling").isZero();
		assertThat(value(1, MovePattern.PAN)).as("jumped").isCloseTo(0.8, within(1e-9));
		assertThat(value(1, RED)).as("the color jumps with it").isEqualTo(1.0);
		advance(2250); // 3 s: fade-in started at 2 s
		assertThat(value(1, DIMMER)).isCloseTo(0.4, within(1e-9));
		assertThat(playback.fade(now).orElseThrow().delay()).as("the show counts the dark phase")
				.isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void aDarkHeadJumpsAtOnceAndWaitsForTheTravel() {
		start(Map.of(
				1L, List.of(move(1, LEFT, 1)),
				2L, List.of(lit(2, 1.0), move(2, RIGHT, 1))), MoveInDark.on());
		go();
		advance(100);
		assertThat(value(1, MovePattern.PAN)).isCloseTo(0.8, within(1e-9));
		assertThat(value(1, DIMMER)).isZero();
		advance(1400); // 1.5 s: travel over
		assertThat(value(1, DIMMER)).isZero();
		advance(1000);
		assertThat(value(1, DIMMER)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void headsThatStayAndOtherFixturesFadeAsUsual() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 0.0), move(2, LEFT, 1))), MoveInDark.on());
		go();
		advance(1000); // same position: an ordinary fade-out over 2 s
		assertThat(value(1, DIMMER)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void theSameMoveRunningOnIsNoChangeOfPosition() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LINE, 1)),
				2L, List.of(lit(2, 0.5), move(1, LINE, 1))), MoveInDark.on());
		go();
		advance(1000);
		assertThat(value(1, DIMMER)).isCloseTo(0.75, within(1e-9));
	}

	@Test
	void aLongerDelayHoldsBackTheDimIn() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 1.0), move(2, RIGHT, 1))), MoveInDark.on(), Duration.ofSeconds(3));
		go();
		advance(2900);
		assertThat(value(1, DIMMER)).isZero();
		advance(1100); // 4 s: one second into the fade-in
		assertThat(value(1, DIMMER)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void aMoveStartsWhenItsHeadsDimIn() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 1.0), move(3, LINE, 1))), MoveInDark.on());
		go();
		advance(2000);
		assertThat(value(1, MovePattern.PAN)).as("waits at the start of the line").isCloseTo(0, within(1e-9));
		advance(2000);
		assertThat(value(1, MovePattern.PAN)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void theStayStartsWhenAllHeadsHaveDimmedIn() {
		var timing = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ZERO, Optional.empty(),
				MoveInDark.on());
		var scenes = List.of(scene(1, SceneTiming.DEFAULT, Optional.empty()),
				scene(2, timing, Optional.of(Duration.ofSeconds(1))), scene(3, SceneTiming.DEFAULT, Optional.empty()));
		playback = new Playback(scenes, Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 1.0), move(2, RIGHT, 1))), Duration.ofSeconds(2), Duration.ofSeconds(2));
		go();
		advance(5000);
		playback.tick(now, B);
		go();
		assertThat(playback.autoFollowRemaining(now)).as("dim out + travel + fade-in + stay")
				.contains(Duration.ofSeconds(5));
	}

	@Test
	void withoutMoveInDarkTheHeadSweepsLit() {
		start(Map.of(
				1L, List.of(lit(1, 1.0), move(1, LEFT, 1)),
				2L, List.of(lit(2, 1.0), move(2, RIGHT, 1))), MoveInDark.OFF);
		go();
		advance(1000);
		assertThat(value(1, DIMMER)).isEqualTo(1.0);
		assertThat(value(1, MovePattern.PAN)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void lightMarkersMoveInTheDarkToo() {
		var timing = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ZERO, Optional.empty(),
				new MoveInDark(true, Optional.of(Duration.ZERO), Optional.of(Duration.ofSeconds(1))));
		playback = new Playback(List.of(scene(1, timing, Optional.empty())),
				Map.of(1L, List.of(lit(1, 1.0), move(1, LEFT, 1))), Duration.ofSeconds(2), Duration.ofSeconds(2));
		playback.useSections(Map.of(1L, List.of(List.of(lit(1, 1.0), move(2, RIGHT, 1)))));
		go();
		advance(3000);
		playback.tick(now, B);
		playback.goToSection(1, now, B);
		advance(500);
		assertThat(value(1, DIMMER)).isZero();
		assertThat(value(1, MovePattern.PAN)).isCloseTo(0.8, within(1e-9));
		advance(1500);
		assertThat(value(1, DIMMER)).isCloseTo(0.5, within(1e-9));
	}

	private void start(Map<Long, List<ActiveComposition>> compositions, MoveInDark moveInDark) {
		start(compositions, moveInDark, Duration.ZERO);
	}

	/** Scene 1 lit and still (after its fade-in), the next GO goes to scene 2. */
	private void start(Map<Long, List<ActiveComposition>> compositions, MoveInDark moveInDark, Duration delay) {
		var timing = new SceneTiming(Optional.empty(), Optional.empty(), delay, Optional.empty(), moveInDark);
		playback = new Playback(List.of(scene(1, SceneTiming.DEFAULT, Optional.empty()),
				scene(2, timing, Optional.empty())), compositions, Duration.ofSeconds(2), Duration.ofSeconds(2));
		go();
		advance(5000);
		playback.tick(now, B);
	}

	private void go() {
		playback.go(now, B);
	}

	private static Scene scene(long id, SceneTiming timing, Optional<Duration> autoFollow) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.of((int) id), "Scene " + id, Optional.empty(), autoFollow,
				timing));
	}

	private static Figure spot(long id, double pan) {
		return new Figure(id, 1, new FigureSpec("Spot", true, List.of(new FigurePoint(pan, 0.5))));
	}

	private static ActiveComposition lit(long lookId, double level) {
		return lit(lookId, level, Map.of());
	}

	private static ActiveComposition lit(long lookId, double level, Map<Attribute, Double> more) {
		var values = new HashMap<>(more);
		values.put(DIMMER, level);
		return ActiveComposition.ofLook(lookId, Look.of(Map.of(1L, new FixtureValues(values))), 1);
	}

	private static ActiveComposition move(long id, Figure figure, long... fixtureIds) {
		var fixtures = Arrays.stream(fixtureIds).mapToObj(MoveFixture::of).toList();
		return ActiveComposition.ofMove(new Move(id, 1, MoveSpec.of("Move " + id, figure.id(), fixtures)), figure);
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}

	private double value(long fixtureId, Attribute attribute) {
		return playback.output(now, B).getOrDefault(fixtureId, FixtureValues.EMPTY).get(attribute).orElse(0.0);
	}
}
