package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

/**
 * Position of a fixture in the stage view, relative from 0 to 1.
 */
public record Position(double x, double y) {

	public Position {
		new Validation()
				.require(x >= 0 && x <= 1, "position.x must be between 0 and 1, was " + x)
				.require(y >= 0 && y <= 1, "position.y must be between 0 and 1, was " + y)
				.throwIfInvalid();
	}
}
