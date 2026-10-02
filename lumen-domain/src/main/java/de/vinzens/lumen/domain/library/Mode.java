package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A DMX mode of a fixture type. The order of the channels corresponds to the DMX offset.
 */
public record Mode(String name, List<ChannelDefinition> channels) {

	public Mode {
		Objects.requireNonNull(name, "name");
		channels = List.copyOf(channels);
	}

	/** Whether the mode has a real dimmer channel; otherwise a virtual dimmer scales the color channels. */
	public boolean hasDimmer() {
		return channels.stream().anyMatch(c -> c.function() == ChannelFunction.DIMMER);
	}

	/** Distinct attributes of this mode in channel order. */
	public List<Attribute> attributes() {
		var attributes = channels.stream().map(ChannelDefinition::attribute).distinct();
		return hasDimmer() ? attributes.toList()
				: Stream.concat(Stream.of(Attribute.of(ChannelFunction.DIMMER)), attributes).toList();
	}

	/** Number of occupied DMX channels (16-bit channels count twice). */
	public int dmxChannelCount() {
		return channels.stream().mapToInt(ChannelDefinition::dmxChannelCount).sum();
	}
}
