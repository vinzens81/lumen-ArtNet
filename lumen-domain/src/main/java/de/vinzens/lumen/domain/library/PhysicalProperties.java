package de.vinzens.lumen.domain.library;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Physical properties needed for display.
 *
 * @param panRange  pan range in degrees
 * @param tiltRange tilt range in degrees
 */
public record PhysicalProperties(OptionalInt panRange, OptionalInt tiltRange) {

	public static final PhysicalProperties NONE = new PhysicalProperties(OptionalInt.empty(), OptionalInt.empty());

	public PhysicalProperties {
		Objects.requireNonNull(panRange, "panRange");
		Objects.requireNonNull(tiltRange, "tiltRange");
	}
}
