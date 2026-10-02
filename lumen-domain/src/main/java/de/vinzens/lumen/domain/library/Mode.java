package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;

/**
 * Ein DMX-Modus eines Gerätetyps. Die Reihenfolge der Kanäle entspricht dem DMX-Offset.
 */
public record Mode(String name, List<ChannelDefinition> channels) {

	public Mode {
		Objects.requireNonNull(name, "name");
		channels = List.copyOf(channels);
	}

	/** Anzahl belegter DMX-Kanäle (16-Bit-Kanäle zählen doppelt). */
	public int dmxChannelCount() {
		return channels.stream().mapToInt(ChannelDefinition::dmxChannelCount).sum();
	}
}
