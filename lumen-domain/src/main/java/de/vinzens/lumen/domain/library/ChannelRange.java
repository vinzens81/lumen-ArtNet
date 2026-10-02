package de.vinzens.lumen.domain.library;

import java.util.Objects;
import java.util.Optional;

/**
 * Named value range of a channel, e.g. "Strobe slow → fast" or a color wheel slot.
 *
 * @param color optional display color in the format {@code #rrggbb}
 */
public record ChannelRange(int from, int to, String label, Optional<String> color) {

	public ChannelRange {
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(color, "color");
	}
}
