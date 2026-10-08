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
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Moves in the playback: positions come only from moves, and a head without a move stays where it is. */
class PlaybackMoveTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final FadeBehaviors B = FadeBehaviors.DEFAULTS;
	/** A line from pan 0 to pan 1 at tilt 0.5, there and back in 8 s. */
	private static final Figure LINE = new Figure(1, 1, new FigureSpec("Line", true,
			List.of(new FigurePoint(0, 0.5), new FigurePoint(1, 0.5))));
	private static final Figure SPOT = new Figure(2, 1, new FigureSpec("Spot", true,
			List.of(new FigurePoint(0.2, 0.8))));

	private static final Figure SPOT_RIGHT = new Figure(3, 1, new FigureSpec("Right", true,
			List.of(new FigurePoint(0.8, 0.8))));

	private Instant now = Instant.parse("2026-10-08T20:00:00Z");
	private Playback current;

	@Test
	void aHeadWithoutAMoveKeepsItsPosition() {
		var playback = playback(Map.of(
				1L, List.of(lit(), move(1, LINE, 1)),
				2L, List.of(lit())), 2);
		playback.go(now, B);
		advance(2000); // a quarter of the way: pan 0.5
		playback.tick(now, B);
		playback.go(now, B); // only the color changes
		advance(1000);
		assertThat(pan(1)).isCloseTo(0.5, within(1e-9));
		advance(5000);
		playback.tick(now, B);
		assertThat(pan(1)).as("after the fade").isCloseTo(0.5, within(1e-9));
		assertThat(tilt(1)).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void theSameMoveContinuesFromSceneToScene() {
		var playback = playback(Map.of(
				1L, List.of(lit(), move(1, LINE, 1)),
				2L, List.of(lit(), move(1, LINE, 1))), 2);
		playback.go(now, B);
		advance(2000);
		playback.tick(now, B);
		playback.go(now, B);
		advance(1000); // continues: 3 s since its start
		assertThat(pan(1)).isCloseTo(0.75, within(1e-9));
	}

	@Test
	void aLitHeadGlidesToItsNewMoveAndADarkOneJumps() {
		var playback = playback(Map.of(
				1L, List.of(lit(), move(2, SPOT, 1)),
				2L, List.of(lit(), move(3, SPOT_RIGHT, 1, 2))), 2);
		playback.go(now, B);
		advance(2000);
		playback.tick(now, B);
		playback.go(now, B);
		advance(1000); // half of the fade-in
		assertThat(pan(1)).as("lit: on its way").isCloseTo(0.5, within(1e-9));
		assertThat(pan(2)).as("dark: at once").isCloseTo(0.8, within(1e-9));
	}

	@Test
	void theLaterOfTwoMovesOnAFixtureWins() {
		var playback = playback(Map.of(1L, List.of(lit(), move(1, LINE, 1), move(2, SPOT, 1))), 0);
		playback.go(now, B);
		advance(3000);
		assertThat(pan(1)).isCloseTo(0.2, within(1e-9));
		assertThat(tilt(1)).isCloseTo(0.8, within(1e-9));
	}

	private static ActiveComposition lit() {
		return ActiveComposition.ofLook(1, Look.of(Map.of(1L, new FixtureValues(Map.of(DIMMER, 1.0)))), 1);
	}

	private static ActiveComposition move(long id, Figure figure, long... fixtureIds) {
		var fixtures = Arrays.stream(fixtureIds).mapToObj(MoveFixture::of).toList();
		return ActiveComposition.ofMove(new Move(id, 1, MoveSpec.of("Move " + id, figure.id(), fixtures)), figure);
	}

	private Playback playback(Map<Long, List<ActiveComposition>> compositions, int fadeSeconds) {
		current = new Playback(List.of(scene(1), scene(2)), compositions, Duration.ofSeconds(fadeSeconds),
				Duration.ofSeconds(fadeSeconds));
		return current;
	}

	private static Scene scene(long id) {
		return new Scene(id, 1, new SceneSpec(SceneNumber.of((int) id), "Scene " + id, Optional.empty(),
				Optional.empty()));
	}

	private void advance(long ms) {
		now = now.plusMillis(ms);
	}

	private double pan(long fixtureId) {
		return value(fixtureId, MovePattern.PAN);
	}

	private double tilt(long fixtureId) {
		return value(fixtureId, MovePattern.TILT);
	}

	private double value(long fixtureId, Attribute attribute) {
		return current.output(now, B).getOrDefault(fixtureId, FixtureValues.EMPTY).get(attribute).orElse(-1.0);
	}

}
