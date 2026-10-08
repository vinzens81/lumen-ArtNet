package de.vinzens.lumen.domain.move;

import de.vinzens.lumen.domain.Validation;

import java.util.List;
import java.util.Objects;

/**
 * Editable properties of a figure: only the shape, without fixtures.
 *
 * @param closed after the last point the way goes back to the first (circles, eights); otherwise an open way
 *               (a journey from A to B)
 * @param points in order, at least one; a single point is a fixed position
 */
public record FigureSpec(String name, boolean closed, List<FigurePoint> points) {

	public static final int MAX_POINTS = 256;

	public FigureSpec {
		Objects.requireNonNull(points, "points");
		points = List.copyOf(points);
		new Validation()
				.requireText(name, "name")
				.require(!points.isEmpty(), "a figure needs at least one point")
				.require(points.size() <= MAX_POINTS, "a figure has at most " + MAX_POINTS + " points")
				.throwIfInvalid();
		name = name.strip();
	}

	public FigureSpec withName(String newName) {
		return new FigureSpec(newName, closed, points);
	}
}
