package de.vinzens.lumen.adapter.web.library;

import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.Mode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record FixtureTypeDto(
		String id,
		String manufacturer,
		String model,
		String category,
		PhysicalDto physical,
		List<ModeDto> modes) {

	record PhysicalDto(Integer panRange, Integer tiltRange) {
	}

	record ModeDto(String name, int dmxChannelCount, List<ChannelDto> channels) {
	}

	/**
	 * @param offset DMX offset within the mode, starting at 1
	 */
	record ChannelDto(
			int offset,
			String function,
			String name,
			int resolution,
			int dmxChannelCount,
			int defaultValue,
			boolean invert,
			String fade,
			List<RangeDto> ranges) {
	}

	record RangeDto(int from, int to, String label, String color) {
	}

	static FixtureTypeDto from(FixtureType type) {
		var physical = type.physical();
		return new FixtureTypeDto(
				type.id(),
				type.manufacturer(),
				type.model(),
				type.category().id(),
				new PhysicalDto(
						physical.panRange().isPresent() ? physical.panRange().getAsInt() : null,
						physical.tiltRange().isPresent() ? physical.tiltRange().getAsInt() : null),
				type.modes().stream().map(FixtureTypeDto::from).toList());
	}

	private static ModeDto from(Mode mode) {
		var channels = new ArrayList<ChannelDto>();
		var offset = 1;
		for (var channel : mode.channels()) {
			channels.add(from(channel, offset));
			offset += channel.dmxChannelCount();
		}
		return new ModeDto(mode.name(), mode.dmxChannelCount(), channels);
	}

	private static ChannelDto from(ChannelDefinition channel, int offset) {
		return new ChannelDto(
				offset,
				channel.function().id(),
				channel.displayName(),
				channel.resolution(),
				channel.dmxChannelCount(),
				channel.defaultValue(),
				channel.invert(),
				channel.effectiveFadeBehavior().name().toLowerCase(Locale.ROOT),
				channel.ranges().stream().map(FixtureTypeDto::from).toList());
	}

	private static RangeDto from(ChannelRange range) {
		return new RangeDto(range.from(), range.to(), range.label(), range.color().orElse(null));
	}
}
