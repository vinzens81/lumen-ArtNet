package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.Validation;

/**
 * A fixture of a move with its own corrections.
 *
 * @param shiftX  own shift of pan, normalized (−1 … 1 = −255 … +255 DMX), on top of the shift of the move: fixtures
 *                hanging at different places point at the same spot
 * @param shiftY  own shift of tilt
 * @param mirrorX pan mirrored around the middle of the figure, e.g. for heads left and right moving symmetrically
 * @param mirrorY tilt mirrored around the middle of the figure
 * @param reverse follows the figure the other way round
 */
public record MoveFixture(long fixtureId, double shiftX, double shiftY, boolean mirrorX, boolean mirrorY,
		boolean reverse) {

	public MoveFixture {
		new Validation()
				.require(shiftX >= -1 && shiftX <= 1 && shiftY >= -1 && shiftY <= 1,
						"the shift of a fixture must be between -255 and 255")
				.throwIfInvalid();
	}

	/** A fixture without corrections. */
	public static MoveFixture of(long fixtureId) {
		return new MoveFixture(fixtureId, 0, 0, false, false, false);
	}
}
