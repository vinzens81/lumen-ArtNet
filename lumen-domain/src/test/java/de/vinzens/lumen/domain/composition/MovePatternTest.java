package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePath;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveRepeat;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.move.OnceStart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MovePatternTest {

	/** A square around (0.5, 0.5), four lines of the same length: a point every quarter round. */
	private static final List<FigurePoint> SQUARE = List.of(new FigurePoint(0.25, 0.25),
			new FigurePoint(0.75, 0.25), new FigurePoint(0.75, 0.75), new FigurePoint(0.25, 0.75));
	private static final List<MoveFixture> FOUR = List.of(MoveFixture.of(1), MoveFixture.of(2), MoveFixture.of(3),
			MoveFixture.of(4));

	@Test
	void loopFollowsTheFigureAtAnEvenSpeed() {
		var move = pattern(spec(FOUR.subList(0, 1)), true);
		assertPosition(move, 0, 0, 0.25, 0.25);
		assertPosition(move, 1, 0, 0.5, 0.25); // 8 s per round: an eighth of the way
		assertPosition(move, 4, 0, 0.75, 0.75);
		assertPosition(move, 8, 0, 0.25, 0.25);
		assertPosition(move, 9, 0, 0.5, 0.25);
	}

	@Test
	void anOpenFigureJumpsBackToItsStartInALoop() {
		var move = pattern(spec(FOUR.subList(0, 1)), false);
		// three lines of 0.5: 8/3 s each
		assertPosition(move, 8 - 1e-9, 0, 0.25, 0.75);
		assertPosition(move, 8, 0, 0.25, 0.25);
	}

	@Test
	void anOffsetLetsEachFixtureStartAtALaterPoint() {
		var move = pattern(withOffset(spec(FOUR), 1), true);
		assertPosition(move, 0, 0, 0.25, 0.25);
		assertPosition(move, 0, 1, 0.75, 0.25);
		assertPosition(move, 0, 2, 0.75, 0.75);
		assertPosition(move, 0, 3, 0.25, 0.75);
		assertPosition(move, 2, 3, 0.25, 0.25); // a quarter round later the fourth is back at point 1
	}

	@Test
	void anOffsetBeyondTheLastPointWrapsAround() {
		var move = pattern(withOffset(spec(FOUR), 3), true);
		assertPosition(move, 0, 2, 0.75, 0.75); // point 1 + 6 = point 3
	}

	@Test
	void pingPongGoesBackEveryOtherRound() {
		var move = pattern(withRepeat(spec(FOUR.subList(0, 1)), MoveRepeat.PING_PONG, OnceStart.SEQUENTIAL),
				false);
		assertPosition(move, 4, 0, 0.75, 0.5);
		assertPosition(move, 8, 0, 0.25, 0.75); // the end of the way, now going back
		assertPosition(move, 12, 0, 0.75, 0.5);
		assertPosition(move, 16, 0, 0.25, 0.25);
	}

	@Test
	void onceInSequenceIsASnakeOnTheSameJourney() {
		var spec = withOffset(withRepeat(spec(FOUR.subList(0, 2)), MoveRepeat.ONCE, OnceStart.SEQUENTIAL), 1);
		var move = pattern(spec, false);
		// open: three lines, the second fixture starts when the first reaches point 2 (after 8/3 s)
		assertPosition(move, 1, 1, 0.25, 0.25);
		assertPosition(move, 8.0 / 3, 0, 0.75, 0.25);
		assertPosition(move, 8.0 / 3, 1, 0.25, 0.25);
		assertPosition(move, 8 + 8.0 / 3, 1, 0.25, 0.75);
		assertPosition(move, 100, 0, 0.25, 0.75);
		assertThat(move.onceSeconds(2)).hasValueSatisfying(end -> assertThat(end).isCloseTo(8 + 8.0 / 3,
				within(1e-9)));
	}

	@Test
	void onceTogetherStartsAllAtTheirOwnPointAndStopsAtTheEnd() {
		var open = pattern(withOffset(withRepeat(spec(FOUR.subList(0, 2)), MoveRepeat.ONCE, OnceStart.TOGETHER), 2),
				false);
		assertPosition(open, 0, 1, 0.75, 0.75);
		assertPosition(open, 4, 1, 0.25, 0.75); // the shorter way of the second fixture is over
		assertPosition(open, 100, 0, 0.25, 0.75);

		var closed = pattern(withOffset(withRepeat(spec(FOUR.subList(0, 2)), MoveRepeat.ONCE, OnceStart.TOGETHER),
				2), true);
		assertPosition(closed, 4, 1, 0.25, 0.25);
		assertPosition(closed, 100, 1, 0.75, 0.75); // one round, back at its own starting point
		assertThat(closed.onceSeconds(2)).contains(8.0);
	}

	@Test
	void reverseRunsTheOtherWayRound() {
		var spec = spec(List.of(new MoveFixture(1, 0, 0, false, false, true)));
		var move = pattern(spec, true);
		assertPosition(move, 2, 0, 0.25, 0.75);
	}

	@Test
	void mirroringFlipsAroundTheMiddleOfTheFigure() {
		var spec = spec(List.of(new MoveFixture(1, 0, 0, true, false, false),
				new MoveFixture(2, 0, 0, false, true, false)));
		var move = pattern(spec, true);
		assertPosition(move, 1, 0, 0.5, 0.25);
		assertPosition(move, 0, 0, 0.75, 0.25);
		assertPosition(move, 0, 1, 0.25, 0.75);
	}

	@Test
	void sizeShiftAndRotationPlaceTheFigure() {
		var base = spec(FOUR.subList(0, 1));
		var scaled = new MoveSpec("Move", 1, base.fixtures(), 0.5, 2, 0.1, -0.1, 0, 8, MoveRepeat.LOOP, 0,
				OnceStart.SEQUENTIAL);
		assertPosition(pattern(scaled, true), 0, 0, 0.375 + 0.1, 0); // tilt 0.5 − 0.5 − 0.1: limited to 0
		var rotated = new MoveSpec("Move", 1, base.fixtures(), 1, 1, 0, 0, 90, 8, MoveRepeat.LOOP, 0,
				OnceStart.SEQUENTIAL);
		// (−0.25, −0.25) around the middle turned by 90° counter-clockwise → (0.25, −0.25)
		assertPosition(pattern(rotated, true), 0, 0, 0.75, 0.25);
	}

	@Test
	void theShiftOfAFixtureComesOnTop() {
		var fixtures = List.of(new MoveFixture(1, -18 / 255.0, 6 / 255.0, false, false, false));
		var point = new FigureSpec("Singer", true, List.of(FigurePoint.ofDmx(128, 100)));
		var move = new MovePattern(new MoveSpec("Singer", 1, fixtures, 1, 1, 0, 0, 0, 8, MoveRepeat.LOOP, 0,
				OnceStart.SEQUENTIAL), new FigurePath(point.points(), point.closed()));
		assertPosition(move, 0, 0, 110 / 255.0, 106 / 255.0);
		assertPosition(move, 5, 0, 110 / 255.0, 106 / 255.0);
	}

	@Test
	void setsOnlyPanAndTiltOfItsOwnFixtures() {
		var move = pattern(spec(FOUR.subList(0, 2)), true);
		assertThat(move.valuesAt(0, 0, 2, 2).values()).containsOnlyKeys(MovePattern.PAN, MovePattern.TILT);
		assertThat(move.valuesAt(0, 0, 2, 9).isEmpty()).isTrue();
		assertThat(move.ownFixtures()).contains(List.of(1L, 2L));
	}

	@Test
	void aMoveContinuesAsTheSameMove() {
		var figure = new Figure(3, 1, new FigureSpec("Square", true, SQUARE));
		var move = new Move(7, 1, spec(FOUR));
		var active = ActiveComposition.ofMove(move, figure);
		assertThat(active.id()).isEqualTo(ActiveComposition.MOVE_IDS + 7);
		assertThat(active.continues(ActiveComposition.ofMove(move, figure))).isTrue();
		assertThat(active.fixtureIds()).containsExactly(1L, 2L, 3L, 4L);
		var mixed = CompositionMixer.contribution(active, 1, FixtureAttributes.ALL);
		assertThat(mixed.get(1L).get(MovePattern.PAN)).hasValueSatisfying(pan -> assertThat(pan)
				.isCloseTo(0.5, within(1e-9)));
	}

	@Test
	void movesValidateTheirSettings() {
		assertThatThrownBy(() -> new MoveSpec("Move", 1, List.of(MoveFixture.of(1), MoveFixture.of(1)), 1, 1, 0, 0,
				0, 8, MoveRepeat.LOOP, 0, OnceStart.SEQUENTIAL))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("twice");
		assertThatThrownBy(() -> new MoveSpec("Move", 1, List.of(), 1, 1, 0, 0, 0, 0, MoveRepeat.LOOP, 0,
				OnceStart.SEQUENTIAL))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("duration");
	}

	private static MoveSpec spec(List<MoveFixture> fixtures) {
		return MoveSpec.of("Move", 1, fixtures);
	}

	private static MoveSpec withOffset(MoveSpec spec, int offset) {
		return new MoveSpec(spec.name(), spec.figureId(), spec.fixtures(), spec.sizeX(), spec.sizeY(), spec.shiftX(),
				spec.shiftY(), spec.rotationDegrees(), spec.durationSeconds(), spec.repeat(), offset,
				spec.onceStart());
	}

	private static MoveSpec withRepeat(MoveSpec spec, MoveRepeat repeat, OnceStart start) {
		return new MoveSpec(spec.name(), spec.figureId(), spec.fixtures(), spec.sizeX(), spec.sizeY(), spec.shiftX(),
				spec.shiftY(), spec.rotationDegrees(), spec.durationSeconds(), repeat, spec.stepOffset(), start);
	}

	private static MovePattern pattern(MoveSpec spec, boolean closed) {
		return new MovePattern(spec, new FigurePath(SQUARE, closed));
	}

	private static void assertPosition(MovePattern move, double seconds, int index, double pan, double tilt) {
		var point = move.position(seconds, index);
		assertThat(point.x()).as("pan at %s s, fixture %s", seconds, index).isCloseTo(pan, within(1e-9));
		assertThat(point.y()).as("tilt at %s s, fixture %s", seconds, index).isCloseTo(tilt, within(1e-9));
	}
}
