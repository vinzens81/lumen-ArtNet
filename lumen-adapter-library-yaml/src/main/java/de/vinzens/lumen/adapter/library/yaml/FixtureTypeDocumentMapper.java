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
 * Bildet ein gelesenes YAML-Dokument auf die Domain ab. Strukturelle Fehler (fehlende Pflichtfelder,
 * unbekannte Funktionen) werden als Issues gesammelt; die fachliche Prüfung übernimmt die Domain.
 */
final class FixtureTypeDocumentMapper {

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
			error("id fehlt");
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
			error("category fehlt (erlaubt: " + allowed(FixtureCategory.values()) + ")");
			return FixtureCategory.OTHER;
		}
		return FixtureCategory.fromId(category).orElseGet(() -> {
			error("Unbekannte category '" + category + "' (erlaubt: " + allowed(FixtureCategory.values()) + ")");
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
		var channels = nullToEmpty(mode.channels()).stream()
				.map(channel -> mapChannel(channel, name))
				.flatMap(Optional::stream)
				.toList();
		return new Mode(name, channels);
	}

	private Optional<ChannelDefinition> mapChannel(ChannelDocument channel, String modeName) {
		var prefix = "Modus '" + modeName + "': ";
		if (channel.function() == null) {
			error(prefix + "Kanal ohne function");
			return Optional.empty();
		}
		var function = ChannelFunction.fromId(channel.function());
		if (function.isEmpty()) {
			error(prefix + "Unbekannte function '" + channel.function() + "'");
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
				error(prefix + "Unbekanntes fade '" + fade + "' (erlaubt: snap, smooth)");
				yield Optional.empty();
			}
		};
	}

	private Optional<ChannelRange> mapRange(RangeDocument range, String prefix) {
		if (range.from() == null || range.to() == null) {
			error(prefix + "Bereich '" + range.label() + "' braucht from und to");
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
