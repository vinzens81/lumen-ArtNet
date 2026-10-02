package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.Validation;

/**
 * Position eines Geräts in der Bühnenansicht, relativ von 0 bis 1.
 */
public record Position(double x, double y) {

	public Position {
		new Validation()
				.require(x >= 0 && x <= 1, "position.x muss zwischen 0 und 1 liegen, ist " + x)
				.require(y >= 0 && y <= 1, "position.y muss zwischen 0 und 1 liegen, ist " + y)
				.throwIfInvalid();
	}
}
