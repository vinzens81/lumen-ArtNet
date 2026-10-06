package de.vinzens.lumen.domain.output;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.Mode;

import java.util.HashMap;

/**
 * Fixtures whose heads have dimmers but the fixture as a whole has none – e.g. a dimmer pack with four channels:
 * their dimmer without head is no channel. Programmer values are kept per channel instead, so nothing hidden ends up
 * in a look.
 */
public final class HeadDimmers {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);

	private HeadDimmers() {
	}

	/**
	 * The dimmer of the whole fixture goes to every head dimmer the values do not set themselves. Values of other
	 * modes stay as they are.
	 */
	public static FixtureValues spread(Mode mode, FixtureValues values) {
		var whole = values.get(DIMMER);
		if (whole.isEmpty() || !mode.dimmersOnlyPerHead()) {
			return values;
		}
		var result = new HashMap<>(values.values());
		result.remove(DIMMER);
		mode.channels().stream()
				.filter(channel -> channel.function() == ChannelFunction.DIMMER && channel.head() > 0)
				.forEach(channel -> result.putIfAbsent(DIMMER.withHead(channel.head()), whole.get()));
		return new FixtureValues(result);
	}
}
