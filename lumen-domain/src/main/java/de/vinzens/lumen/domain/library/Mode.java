package de.vinzens.lumen.domain.library;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * A DMX mode of a fixture type. The order of the channels corresponds to the DMX offset. Channels of a multi-pixel
 * fixture belong to a head (segment, pixel); see {@link ChannelDefinition#head()}.
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

	/** Number of heads of a multi-pixel fixture, 0 for a fixture without heads. */
	public int heads() {
		return channels.stream().mapToInt(ChannelDefinition::head).max().orElse(0);
	}

	/** Whether the heads have dimmer channels of their own. */
	public boolean hasHeadDimmers() {
		return channels.stream().anyMatch(c -> c.function() == ChannelFunction.DIMMER && c.head() > 0);
	}

	/**
	 * Only the heads have dimmer channels, the fixture as a whole has none – e.g. a dimmer pack with four channels.
	 * Its dimmer without head is then no channel but a value for all heads.
	 */
	public boolean dimmersOnlyPerHead() {
		return hasHeadDimmers() && channels.stream()
				.noneMatch(c -> c.function() == ChannelFunction.DIMMER && c.head() == 0);
	}

	/**
	 * Distinct attributes of this mode in channel order. A fixture without a dimmer channel gets a virtual dimmer;
	 * heads without a dimmer channel get a virtual one each. Every attribute of the heads also exists for the whole
	 * fixture, setting all heads at once.
	 */
	public List<Attribute> attributes() {
		var attributes = new LinkedHashSet<Attribute>();
		if (!hasDimmer()) {
			attributes.add(Attribute.of(ChannelFunction.DIMMER));
		}
		var heads = heads();
		if (heads > 0) {
			channels.forEach(channel -> attributes.add(channel.attribute().withoutHead()));
		}
		channels.forEach(channel -> attributes.add(channel.attribute()));
		if (!hasHeadDimmers()) {
			for (int head = 1; head <= heads; head++) {
				attributes.add(Attribute.of(ChannelFunction.DIMMER).withHead(head));
			}
		}
		return List.copyOf(attributes);
	}

	/** Number of occupied DMX channels (16-bit channels count twice). */
	public int dmxChannelCount() {
		return channels.stream().mapToInt(ChannelDefinition::dmxChannelCount).sum();
	}
}
