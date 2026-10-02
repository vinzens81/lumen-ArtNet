package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;

/**
 * A DMX mode of a fixture type. The order of the channels corresponds to the DMX offset.
 */
public record Mode(String name, List<ChannelDefinition> channels) {

	public Mode {
		Objects.requireNonNull(name, "name");
		channels = List.copyOf(channels);
	}

	/** Number of occupied DMX channels (16-bit channels count twice). */
	public int dmxChannelCount() {
		return channels.stream().mapToInt(ChannelDefinition::dmxChannelCount).sum();
	}
}
