package de.vinzens.lumen.domain.library;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A logical channel of a mode. With 16 bit it occupies two DMX channels (coarse + fine).
 *
 * @param name         display name, required for {@link ChannelFunction#GENERIC}
 * @param resolution   8 or 16 bit
 * @param defaultValue default value; 0–65535 for 16 bit
 * @param fadeBehavior overridden fade behavior, otherwise {@link ChannelFunction#defaultFade()}
 * @param head         0 = the whole fixture, 1… = a head of a multi-pixel fixture
 */
public record ChannelDefinition(
		ChannelFunction function,
		Optional<String> name,
		int resolution,
		int defaultValue,
		boolean invert,
		List<ChannelRange> ranges,
		Optional<FadeBehavior> fadeBehavior,
		int head) {

	public static final int RESOLUTION_8_BIT = 8;
	public static final int RESOLUTION_16_BIT = 16;

	public ChannelDefinition {
		Objects.requireNonNull(function, "function");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(fadeBehavior, "fadeBehavior");
		ranges = List.copyOf(ranges);
		if (head < 0) {
			throw new IllegalArgumentException("head must not be negative");
		}
	}

	/** A channel of the whole fixture. */
	public ChannelDefinition(ChannelFunction function, Optional<String> name, int resolution, int defaultValue,
			boolean invert, List<ChannelRange> ranges, Optional<FadeBehavior> fadeBehavior) {
		this(function, name, resolution, defaultValue, invert, ranges, fadeBehavior, 0);
	}

	/** The same channel for a head of a multi-pixel fixture. */
	public ChannelDefinition forHead(int newHead) {
		return new ChannelDefinition(function, name, resolution, defaultValue, invert, ranges, fadeBehavior, newHead);
	}

	/** Number of occupied DMX channels. */
	public int dmxChannelCount() {
		return resolution == RESOLUTION_16_BIT ? 2 : 1;
	}

	public int maxValue() {
		return resolution == RESOLUTION_16_BIT ? 65_535 : 255;
	}

	public FadeBehavior effectiveFadeBehavior() {
		return fadeBehavior.orElse(function.defaultFade());
	}

	/** The attribute controlled by this channel. */
	public Attribute attribute() {
		var attribute = function == ChannelFunction.GENERIC ? Attribute.generic(name.orElse("")) : Attribute.of(function);
		return attribute.withHead(head);
	}

	/** Display name: explicit name or the function id, with the head of a multi-pixel fixture. */
	public String displayName() {
		var base = name.orElse(function.id());
		return head == 0 ? base : base + " " + head;
	}
}
