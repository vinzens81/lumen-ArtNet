package de.vinzens.lumen.domain.library;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Physikalische Eigenschaften, die für die Anzeige gebraucht werden.
 *
 * @param panRange  Pan-Bereich in Grad
 * @param tiltRange Tilt-Bereich in Grad
 */
public record PhysicalProperties(OptionalInt panRange, OptionalInt tiltRange) {

	public static final PhysicalProperties NONE = new PhysicalProperties(OptionalInt.empty(), OptionalInt.empty());

	public PhysicalProperties {
		Objects.requireNonNull(panRange, "panRange");
		Objects.requireNonNull(tiltRange, "tiltRange");
	}
}
