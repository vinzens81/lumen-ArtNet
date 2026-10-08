package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePath;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveRepeat;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.move.OnceStart;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A move: its fixtures follow a figure, each from its own starting point, at an even speed along the way. Sets only
 * pan and tilt.
 *
 * <ol>
 * <li>progress of fixture {@code i}: loop {@code frac(t / duration + share(i × offset))}, ping-pong the same with
 * every second round backwards, once from the start to the end of the way (see {@link OnceStart}); reverse
 * {@code 1 − p}</li>
 * <li>point on the way at that share of its length</li>
 * <li>mirrored around the middle of the figure (the middle of its bounding box)</li>
 * <li>{@code middle + shift + own shift + rotation(size ⊙ (point − middle))}, limited to 0–255</li>
 * </ol>
 */
public final class MovePattern implements Pattern {

	public static final Attribute PAN = Attribute.of(ChannelFunction.PAN);
	public static final Attribute TILT = Attribute.of(ChannelFunction.TILT);

	private final MoveSpec spec;
	private final FigurePath path;
	private final Map<Long, Integer> indexes = new HashMap<>();

	public MovePattern(MoveSpec spec, FigurePath path) {
		this.spec = Objects.requireNonNull(spec, "spec");
		this.path = Objects.requireNonNull(path, "path");
		for (int i = 0; i < spec.fixtures().size(); i++) {
			indexes.put(spec.fixtures().get(i).fixtureId(), i);
		}
	}

	public static MovePattern of(MoveSpec spec, Figure figure) {
		return new MovePattern(spec, figure.path());
	}

	public MoveSpec spec() {
		return spec;
	}

	public FigurePath path() {
		return path;
	}

	@Override
	public PatternKind kind() {
		return PatternKind.MOVE;
	}

	@Override
	public Optional<List<Long>> ownFixtures() {
		return Optional.of(spec.fixtureIds());
	}

	/** Once: when the last fixture has reached the end of its way. */
	@Override
	public Optional<Double> onceSeconds(int count) {
		if (spec.repeat() != MoveRepeat.ONCE) {
			return Optional.empty();
		}
		var rounds = spec.onceStart() == OnceStart.TOGETHER ? 1
				: delayRounds((long) Math.max(0, spec.fixtures().size() - 1) * spec.stepOffset()) + 1;
		return Optional.of(rounds * spec.durationSeconds());
	}

	@Override
	public FixtureValues valuesAt(double seconds, int index, int count, long fixtureId) {
		var own = indexes.get(fixtureId);
		if (own == null) {
			return FixtureValues.EMPTY;
		}
		var point = position(seconds, own);
		return new FixtureValues(Map.of(PAN, point.x(), TILT, point.y()));
	}

	/** Where the fixture at {@code index} of the move points, {@code seconds} after the move started. */
	public FigurePoint position(double seconds, int index) {
		var fixture = spec.fixtures().get(index);
		var share = share(Math.max(0, seconds) / spec.durationSeconds(), (long) index * spec.stepOffset());
		if (fixture.reverse()) {
			share = 1 - share;
		}
		return place(path.at(share), fixture);
	}

	/** Progress along the way for a fixture starting {@code startStep} points later than the first. */
	private double share(double rounds, long startStep) {
		var startShare = path.shareAt((int) Math.floorMod(startStep, (long) path.pointCount()));
		return switch (spec.repeat()) {
			case LOOP -> Pattern.cycle(rounds + startShare);
			case PING_PONG -> {
				var progress = rounds + startShare;
				var round = (long) Math.floor(progress);
				var within = progress - round;
				yield round % 2 == 0 ? within : 1 - within;
			}
			case ONCE -> switch (spec.onceStart()) {
				case SEQUENTIAL -> Math.min(1, Math.max(0, rounds - delayRounds(startStep)));
				// a closed figure: one round back to the own starting point; an open one: to its end
				case TOGETHER -> path.closed() ? startShare + Math.min(1, rounds) : Math.min(1, startShare + rounds);
			};
		};
	}

	/** Rounds it takes from the first point to the point {@code startStep} steps later (past the end: more rounds). */
	private double delayRounds(long startStep) {
		var n = path.pointCount();
		return Math.floorDiv(startStep, n) + path.shareAt((int) Math.floorMod(startStep, (long) n));
	}

	/** Mirror, size, rotation and shift, limited to the DMX range. */
	private FigurePoint place(FigurePoint point, MoveFixture fixture) {
		var center = path.center();
		var x = fixture.mirrorX() ? 2 * center.x() - point.x() : point.x();
		var y = fixture.mirrorY() ? 2 * center.y() - point.y() : point.y();
		var dx = (x - center.x()) * spec.sizeX();
		var dy = (y - center.y()) * spec.sizeY();
		var angle = Math.toRadians(spec.rotationDegrees());
		var cos = Math.cos(angle);
		var sin = Math.sin(angle);
		var rx = dx * cos - dy * sin;
		var ry = dx * sin + dy * cos;
		return new FigurePoint(clamp(center.x() + spec.shiftX() + fixture.shiftX() + rx),
				clamp(center.y() + spec.shiftY() + fixture.shiftY() + ry));
	}

	private static double clamp(double value) {
		return Math.min(1, Math.max(0, value));
	}
}
