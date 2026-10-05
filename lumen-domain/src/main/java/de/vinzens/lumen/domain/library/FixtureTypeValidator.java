package de.vinzens.lumen.domain.library;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Semantic validation of a fixture type. Returns all issues found instead of stopping at the first one.
 */
public final class FixtureTypeValidator {

	public static final int DMX_UNIVERSE_SIZE = 512;

	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}");

	private FixtureTypeValidator() {
	}

	public static List<LibraryIssue> validate(FixtureType type, String source) {
		var issues = new ArrayList<LibraryIssue>();
		var id = type.id();

		if (!ID_PATTERN.matcher(id).matches()) {
			issues.add(LibraryIssue.error(source, id,
					"Invalid id '" + id + "': only lowercase letters, digits and hyphens are allowed"));
		}
		if (type.manufacturer().isBlank()) {
			issues.add(LibraryIssue.error(source, id, "manufacturer is missing"));
		}
		if (type.model().isBlank()) {
			issues.add(LibraryIssue.error(source, id, "model is missing"));
		}
		if (type.modes().isEmpty()) {
			issues.add(LibraryIssue.error(source, id, "At least one mode is required"));
		}

		var modeNames = new HashSet<String>();
		for (var mode : type.modes()) {
			if (mode.name().isBlank()) {
				issues.add(LibraryIssue.error(source, id, "Mode without name"));
			} else if (!modeNames.add(mode.name())) {
				issues.add(LibraryIssue.error(source, id, "Mode '" + mode.name() + "' is defined twice"));
			}
			validateMode(mode, source, id, issues);
		}
		return issues;
	}

	private static void validateMode(Mode mode, String source, String id, List<LibraryIssue> issues) {
		var prefix = "Mode '" + mode.name() + "': ";
		if (mode.channels().isEmpty()) {
			issues.add(LibraryIssue.error(source, id, prefix + "at least one channel is required"));
		}
		if (mode.dmxChannelCount() > DMX_UNIVERSE_SIZE) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "occupies " + mode.dmxChannelCount() + " channels, maximum is " + DMX_UNIVERSE_SIZE));
		}

		// the same function on different heads of a multi-pixel fixture is fine
		Set<Attribute> seen = new HashSet<>();
		for (int i = 0; i < mode.channels().size(); i++) {
			var channel = mode.channels().get(i);
			var channelPrefix = prefix + "channel " + (i + 1) + " (" + channel.function().id() + "): ";

			if (channel.function() != ChannelFunction.GENERIC && !seen.add(channel.attribute())) {
				issues.add(LibraryIssue.warning(source, id,
						channelPrefix + "function occurs more than once"));
			}
			validateChannel(channel, channelPrefix, source, id, issues);
		}
	}

	private static void validateChannel(ChannelDefinition channel, String prefix, String source, String id,
			List<LibraryIssue> issues) {
		if (channel.function() == ChannelFunction.GENERIC && channel.name().filter(n -> !n.isBlank()).isEmpty()) {
			issues.add(LibraryIssue.error(source, id, prefix + "generic channels need a name"));
		}
		if (channel.resolution() != ChannelDefinition.RESOLUTION_8_BIT
				&& channel.resolution() != ChannelDefinition.RESOLUTION_16_BIT) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "resolution must be 8 or 16, was " + channel.resolution()));
			return;
		}
		if (channel.defaultValue() < 0 || channel.defaultValue() > channel.maxValue()) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "default must be between 0 and " + channel.maxValue() + ", was "
							+ channel.defaultValue()));
		}
		validateRanges(channel.ranges(), prefix, source, id, issues);
	}

	private static void validateRanges(List<ChannelRange> ranges, String prefix, String source, String id,
			List<LibraryIssue> issues) {
		for (var range : ranges) {
			var rangeText = "range " + range.from() + "–" + range.to() + " ('" + range.label() + "'): ";
			if (range.from() < 0 || range.to() > 255 || range.from() > range.to()) {
				issues.add(LibraryIssue.error(source, id,
						prefix + rangeText + "from/to must be 0–255 and from ≤ to"));
			}
			if (range.label().isBlank()) {
				issues.add(LibraryIssue.error(source, id, prefix + rangeText + "label is missing"));
			}
			range.color()
					.filter(color -> !COLOR_PATTERN.matcher(color).matches())
					.ifPresent(color -> issues.add(LibraryIssue.error(source, id,
							prefix + rangeText + "color '" + color + "' must have the format #rrggbb")));
		}

		var sorted = ranges.stream().sorted(Comparator.comparingInt(ChannelRange::from)).toList();
		for (int i = 1; i < sorted.size(); i++) {
			var previous = sorted.get(i - 1);
			var current = sorted.get(i);
			if (current.from() <= previous.to()) {
				issues.add(LibraryIssue.error(source, id, prefix + "ranges '" + previous.label() + "' and '"
						+ current.label() + "' overlap"));
			}
		}
	}
}
