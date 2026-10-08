package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.Validation;

/**
 * A point of a figure in the pan/tilt grid, normalized: {@code x} = pan, {@code y} = tilt, both 0.0–1.0
 * (= DMX 0–255, finer than one DMX step).
 */
public record FigurePoint(double x, double y) {

	public FigurePoint {
		new Validation()
				.require(x >= 0 && x <= 1 && y >= 0 && y <= 1, "a point must be between 0 and 255, was ("
						+ Math.round(x * 255) + ", " + Math.round(y * 255) + ")")
				.throwIfInvalid();
	}

	/** A point from DMX values (0–255). */
	public static FigurePoint ofDmx(double x, double y) {
		return new FigurePoint(x / 255, y / 255);
	}
}
