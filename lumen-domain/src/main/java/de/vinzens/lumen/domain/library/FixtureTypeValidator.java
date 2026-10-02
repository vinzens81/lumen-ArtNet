package de.vinzens.lumen.domain.library;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fachliche Prüfung eines Gerätetyps. Liefert alle gefundenen Probleme statt beim ersten abzubrechen.
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
					"Ungültige id '" + id + "': erlaubt sind Kleinbuchstaben, Ziffern und Bindestriche"));
		}
		if (type.manufacturer().isBlank()) {
			issues.add(LibraryIssue.error(source, id, "manufacturer fehlt"));
		}
		if (type.model().isBlank()) {
			issues.add(LibraryIssue.error(source, id, "model fehlt"));
		}
		if (type.modes().isEmpty()) {
			issues.add(LibraryIssue.error(source, id, "Mindestens ein Modus ist erforderlich"));
		}

		var modeNames = new HashSet<String>();
		for (var mode : type.modes()) {
			if (mode.name().isBlank()) {
				issues.add(LibraryIssue.error(source, id, "Modus ohne Namen"));
			} else if (!modeNames.add(mode.name())) {
				issues.add(LibraryIssue.error(source, id, "Modus '" + mode.name() + "' ist doppelt definiert"));
			}
			validateMode(mode, source, id, issues);
		}
		return issues;
	}

	private static void validateMode(Mode mode, String source, String id, List<LibraryIssue> issues) {
		var prefix = "Modus '" + mode.name() + "': ";
		if (mode.channels().isEmpty()) {
			issues.add(LibraryIssue.error(source, id, prefix + "Mindestens ein Kanal ist erforderlich"));
		}
		if (mode.dmxChannelCount() > DMX_UNIVERSE_SIZE) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "belegt " + mode.dmxChannelCount() + " Kanäle, maximal " + DMX_UNIVERSE_SIZE));
		}

		Set<ChannelFunction> seen = new HashSet<>();
		for (int i = 0; i < mode.channels().size(); i++) {
			var channel = mode.channels().get(i);
			var channelPrefix = prefix + "Kanal " + (i + 1) + " (" + channel.function().id() + "): ";

			if (channel.function() != ChannelFunction.GENERIC && !seen.add(channel.function())) {
				issues.add(LibraryIssue.warning(source, id,
						channelPrefix + "Funktion kommt mehrfach vor"));
			}
			validateChannel(channel, channelPrefix, source, id, issues);
		}
	}

	private static void validateChannel(ChannelDefinition channel, String prefix, String source, String id,
			List<LibraryIssue> issues) {
		if (channel.function() == ChannelFunction.GENERIC && channel.name().filter(n -> !n.isBlank()).isEmpty()) {
			issues.add(LibraryIssue.error(source, id, prefix + "generic-Kanäle brauchen einen name"));
		}
		if (channel.resolution() != ChannelDefinition.RESOLUTION_8_BIT
				&& channel.resolution() != ChannelDefinition.RESOLUTION_16_BIT) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "resolution muss 8 oder 16 sein, ist " + channel.resolution()));
			return;
		}
		if (channel.defaultValue() < 0 || channel.defaultValue() > channel.maxValue()) {
			issues.add(LibraryIssue.error(source, id,
					prefix + "default muss zwischen 0 und " + channel.maxValue() + " liegen, ist "
							+ channel.defaultValue()));
		}
		validateRanges(channel.ranges(), prefix, source, id, issues);
	}

	private static void validateRanges(List<ChannelRange> ranges, String prefix, String source, String id,
			List<LibraryIssue> issues) {
		for (var range : ranges) {
			var rangeText = "Bereich " + range.from() + "–" + range.to() + " ('" + range.label() + "'): ";
			if (range.from() < 0 || range.to() > 255 || range.from() > range.to()) {
				issues.add(LibraryIssue.error(source, id,
						prefix + rangeText + "from/to müssen 0–255 sein und from ≤ to"));
			}
			if (range.label().isBlank()) {
				issues.add(LibraryIssue.error(source, id, prefix + rangeText + "label fehlt"));
			}
			range.color()
					.filter(color -> !COLOR_PATTERN.matcher(color).matches())
					.ifPresent(color -> issues.add(LibraryIssue.error(source, id,
							prefix + rangeText + "Farbe '" + color + "' muss das Format #rrggbb haben")));
		}

		var sorted = ranges.stream().sorted(Comparator.comparingInt(ChannelRange::from)).toList();
		for (int i = 1; i < sorted.size(); i++) {
			var previous = sorted.get(i - 1);
			var current = sorted.get(i);
			if (current.from() <= previous.to()) {
				issues.add(LibraryIssue.error(source, id, prefix + "Bereiche '" + previous.label() + "' und '"
						+ current.label() + "' überlappen"));
			}
		}
	}
}
