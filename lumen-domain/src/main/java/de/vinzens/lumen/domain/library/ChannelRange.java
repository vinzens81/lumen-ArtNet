package de.vinzens.lumen.domain.library;

import java.util.Objects;
import java.util.Optional;

/**
 * Benannter Wertebereich eines Kanals, z.B. „Strobe langsam → schnell“ oder ein Farbrad-Slot.
 *
 * @param color optionale Anzeigefarbe im Format {@code #rrggbb}
 */
public record ChannelRange(int from, int to, String label, Optional<String> color) {

	public ChannelRange {
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(color, "color");
	}
}
