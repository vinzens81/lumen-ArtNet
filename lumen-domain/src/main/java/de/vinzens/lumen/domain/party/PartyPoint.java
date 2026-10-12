package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;

/**
 * A point touched on a gradient or pad field, both 0–1: {@code x} from the left, {@code y} from the bottom. A
 * gradient uses only {@code x}.
 */
public record PartyPoint(double x, double y) {

	/** The middle of a pad. */
	public static final PartyPoint CENTER = new PartyPoint(0.5, 0.5);

	public PartyPoint {
		new Validation()
				.require(x >= 0 && x <= 1, "the point must be between 0 and 1, was x " + x)
				.require(y >= 0 && y <= 1, "the point must be between 0 and 1, was y " + y)
				.throwIfInvalid();
	}

	/** A point on a gradient. */
	public static PartyPoint along(double x) {
		return new PartyPoint(x, 0.5);
	}
}
