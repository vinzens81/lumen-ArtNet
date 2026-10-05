package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.ChannelDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.ModeDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.RangeDocument;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.ChannelRange;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Maps a parsed YAML document to the domain. Structural errors (missing required fields, unknown
 * functions) are collected as issues; semantic validation is done by the domain.
 */
final class FixtureTypeDocumentMapper {

	/** More heads than DMX channels in a universe make no sense. */
	static final int MAX_HEADS = 512;

	private final String source;
	private final List<LibraryIssue> issues = new ArrayList<>();
	private String typeId;

	private FixtureTypeDocumentMapper(String source) {
		this.source = source;
	}

	record Result(Optional<FixtureType> fixtureType, List<LibraryIssue> issues) {
	}

	static Result map(FixtureTypeDocument document, String source) {
		var mapper = new FixtureTypeDocumentMapper(source);
		var type = mapper.mapType(document);
		var hasErrors = mapper.issues.stream().anyMatch(LibraryIssue::isError);
		return new Result(hasErrors ? Optional.empty() : Optional.of(type), List.copyOf(mapper.issues));
	}

	private FixtureType mapType(FixtureTypeDocument document) {
		typeId = document.id();
		if (typeId == null || typeId.isBlank()) {
			error("id is missing");
			typeId = null;
		}
		var category = mapCategory(document.category());
		var modes = nullToEmpty(document.modes()).stream().map(this::mapMode).toList();
		return new FixtureType(
				Objects.requireNonNullElse(typeId, ""),
				Objects.requireNonNullElse(document.manufacturer(), ""),
				Objects.requireNonNullElse(document.model(), ""),
				category,
				mapPhysical(document.physical()),
				modes);
	}

	private FixtureCategory mapCategory(String category) {
		if (category == null) {
			error("category is missing (allowed: " + allowed(FixtureCategory.values()) + ")");
			return FixtureCategory.OTHER;
		}
		return FixtureCategory.fromId(category).orElseGet(() -> {
			error("Unknown category '" + category + "' (allowed: " + allowed(FixtureCategory.values()) + ")");
			return FixtureCategory.OTHER;
		});
	}

	private static PhysicalProperties mapPhysical(FixtureTypeDocument.PhysicalDocument physical) {
		if (physical == null) {
			return PhysicalProperties.NONE;
		}
		return new PhysicalProperties(optionalInt(physical.panRange()), optionalInt(physical.tiltRange()));
	}

	private Mode mapMode(ModeDocument mode) {
		var name = Objects.requireNonNullElse(mode.name(), "");
		var channels = new ArrayList<>(nullToEmpty(mode.channels()).stream()
				.map(channel -> mapChannel(channel, name))
				.flatMap(Optional::stream)
				.toList());
		if (mode.heads() != null) {
			var heads = mapHeads(mode.heads(), name);
			channels.addAll(Boolean.TRUE.equals(mode.heads().first()) ? 0 : channels.size(), heads);
		}
		return new Mode(name, channels);
	}

	/** The head channels repeated for every head, numbered from 1. */
	private List<ChannelDefinition> mapHeads(FixtureTypeDocument.HeadsDocument heads, String modeName) {
		var count = Objects.requireNonNullElse(heads.count(), 0);
		if (count < 1 || count > MAX_HEADS) {
			error("Mode '" + modeName + "': heads.count must be between 1 and " + MAX_HEADS);
			return List.of();
		}
		var perHead = nullToEmpty(heads.channels()).stream()
				.map(channel -> mapChannel(channel, modeName))
				.flatMap(Optional::stream)
				.toList();
		if (perHead.isEmpty()) {
			error("Mode '" + modeName + "': heads need channels");
		}
		var result = new ArrayList<ChannelDefinition>();
		for (int head = 1; head <= count; head++) {
			for (var channel : perHead) {
				result.add(channel.forHead(head));
			}
		}
		return result;
	}

	private Optional<ChannelDefinition> mapChannel(ChannelDocument channel, String modeName) {
		var prefix = "Mode '" + modeName + "': ";
		if (channel.function() == null) {
			error(prefix + "channel without function");
			return Optional.empty();
		}
		var function = ChannelFunction.fromId(channel.function());
		if (function.isEmpty()) {
			error(prefix + "unknown function '" + channel.function() + "'");
			return Optional.empty();
		}
		var fade = Optional.ofNullable(channel.fade()).flatMap(value -> mapFade(value, prefix));
		var ranges = nullToEmpty(channel.ranges()).stream()
				.map(range -> mapRange(range, prefix))
				.flatMap(Optional::stream)
				.toList();
		return Optional.of(new ChannelDefinition(
				function.get(),
				Optional.ofNullable(channel.name()),
				Objects.requireNonNullElse(channel.resolution(), ChannelDefinition.RESOLUTION_8_BIT),
				Objects.requireNonNullElse(channel.defaultValue(), 0),
				Boolean.TRUE.equals(channel.invert()),
				ranges,
				fade));
	}

	private Optional<FadeBehavior> mapFade(String fade, String prefix) {
		return switch (fade) {
			case "snap" -> Optional.of(FadeBehavior.SNAP);
			case "smooth" -> Optional.of(FadeBehavior.SMOOTH);
			default -> {
				error(prefix + "unknown fade '" + fade + "' (allowed: snap, smooth)");
				yield Optional.empty();
			}
		};
	}

	private Optional<ChannelRange> mapRange(RangeDocument range, String prefix) {
		if (range.from() == null || range.to() == null) {
			error(prefix + "range '" + range.label() + "' needs from and to");
			return Optional.empty();
		}
		return Optional.of(new ChannelRange(range.from(), range.to(),
				Objects.requireNonNullElse(range.label(), ""), Optional.ofNullable(range.color())));
	}

	private void error(String message) {
		issues.add(LibraryIssue.error(source, typeId, message));
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static OptionalInt optionalInt(Integer value) {
		return value == null ? OptionalInt.empty() : OptionalInt.of(value);
	}

	private static String allowed(FixtureCategory[] categories) {
		return Arrays.stream(categories).map(FixtureCategory::id).collect(Collectors.joining(", "));
	}
}
