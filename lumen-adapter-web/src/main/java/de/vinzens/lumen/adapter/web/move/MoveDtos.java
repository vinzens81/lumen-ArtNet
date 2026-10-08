package de.vinzens.lumen.adapter.web.move;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveRepeat;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.move.OnceStart;

import java.util.List;
import java.util.Objects;

/**
 * JSON form of figures and moves. Coordinates and shifts are DMX values (pan = x, tilt = y, 0–255, with decimals for
 * a finer position), sizes in percent, times in milliseconds, enum values lower case.
 */
public final class MoveDtos {

	private static final double DMX = 255;

	private MoveDtos() {
	}

	/** A point of a figure in DMX values. */
	public record PointDto(double x, double y) {

		FigurePoint toPoint() {
			return FigurePoint.ofDmx(x, y);
		}

		static PointDto from(FigurePoint point) {
			return new PointDto(point.x() * DMX, point.y() * DMX);
		}
	}

	/** @param closed after the last point back to the first, {@code null} = true */
	public record FigureRequest(String name, Boolean closed, List<PointDto> points) {

		public FigureSpec toSpec() {
			return new FigureSpec(name, !Boolean.FALSE.equals(closed),
					(points == null ? List.<PointDto>of() : points).stream().map(PointDto::toPoint).toList());
		}
	}

	public record FigureResponse(long id, String name, boolean closed, List<PointDto> points) {

		public static FigureResponse from(Figure figure) {
			var spec = figure.spec();
			return new FigureResponse(figure.id(), spec.name(), spec.closed(),
					spec.points().stream().map(PointDto::from).toList());
		}
	}

	/**
	 * A fixture of a move with its own corrections.
	 *
	 * @param shiftX own shift of pan in DMX values (−255 … 255), {@code null} = 0
	 * @param shiftY own shift of tilt
	 */
	public record MoveFixtureDto(Long fixtureId, Double shiftX, Double shiftY, Boolean mirrorX, Boolean mirrorY,
			Boolean reverse) {

		MoveFixture toFixture() {
			if (fixtureId == null) {
				throw new DomainValidationException(List.of("fixtureId of a move fixture is missing"));
			}
			return new MoveFixture(fixtureId, Objects.requireNonNullElse(shiftX, 0.0) / DMX,
					Objects.requireNonNullElse(shiftY, 0.0) / DMX, Boolean.TRUE.equals(mirrorX),
					Boolean.TRUE.equals(mirrorY), Boolean.TRUE.equals(reverse));
		}

		static MoveFixtureDto from(MoveFixture fixture) {
			return new MoveFixtureDto(fixture.fixtureId(), fixture.shiftX() * DMX, fixture.shiftY() * DMX,
					fixture.mirrorX(), fixture.mirrorY(), fixture.reverse());
		}
	}

	/**
	 * Settings of a move; missing values take the defaults (100 %, no shift or rotation, 8 s, loop, offset 0,
	 * sequential).
	 *
	 * @param sizeX      scale of pan in percent
	 * @param shiftX     shift of the whole figure in DMX values
	 * @param rotation   degrees, counter-clockwise
	 * @param durationMs time for one round of the figure
	 * @param repeat     loop, once or ping_pong
	 * @param stepOffset points by which each further fixture starts later
	 * @param onceStart  sequential or together
	 */
	public record MoveRequest(String name, Long figureId, List<MoveFixtureDto> fixtures, Double sizeX, Double sizeY,
			Double shiftX, Double shiftY, Double rotation, Long durationMs, String repeat, Integer stepOffset,
			String onceStart) {

		public MoveSpec toSpec() {
			if (figureId == null) {
				throw new DomainValidationException(List.of("figureId is missing"));
			}
			return new MoveSpec(name, figureId,
					(fixtures == null ? List.<MoveFixtureDto>of() : fixtures).stream().map(MoveFixtureDto::toFixture)
							.toList(),
					Objects.requireNonNullElse(sizeX, 100.0) / 100, Objects.requireNonNullElse(sizeY, 100.0) / 100,
					Objects.requireNonNullElse(shiftX, 0.0) / DMX, Objects.requireNonNullElse(shiftY, 0.0) / DMX,
					Objects.requireNonNullElse(rotation, 0.0),
					durationMs == null ? MoveSpec.DEFAULT_DURATION : durationMs / 1000.0,
					repeat == null ? MoveRepeat.LOOP : MoveRepeat.fromId(repeat),
					Objects.requireNonNullElse(stepOffset, 0),
					onceStart == null ? OnceStart.SEQUENTIAL : OnceStart.fromId(onceStart));
		}
	}

	public record MoveResponse(long id, String name, long figureId, List<MoveFixtureDto> fixtures, double sizeX,
			double sizeY, double shiftX, double shiftY, double rotation, long durationMs, String repeat,
			int stepOffset, String onceStart) {

		public static MoveResponse from(Move move) {
			var spec = move.spec();
			return new MoveResponse(move.id(), spec.name(), spec.figureId(),
					spec.fixtures().stream().map(MoveFixtureDto::from).toList(), spec.sizeX() * 100,
					spec.sizeY() * 100, spec.shiftX() * DMX, spec.shiftY() * DMX, spec.rotationDegrees(),
					Math.round(spec.durationSeconds() * 1000), spec.repeat().id(), spec.stepOffset(),
					spec.onceStart().id());
		}
	}

	/**
	 * "Record position" from the programmer.
	 *
	 * @param moveId     the move to update, {@code null} = a new move named {@code name}
	 * @param fixtureIds the fixtures whose programmer positions are taken
	 */
	public record RecordPositionRequest(Long moveId, String name, List<Long> fixtureIds) {
	}
}
