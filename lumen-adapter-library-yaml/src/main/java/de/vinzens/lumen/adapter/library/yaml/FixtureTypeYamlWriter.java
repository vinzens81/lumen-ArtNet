package de.vinzens.lumen.adapter.library.yaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.ChannelDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.HeadsDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.ModeDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.PhysicalDocument;
import de.vinzens.lumen.adapter.library.yaml.FixtureTypeDocument.RangeDocument;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.Mode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.util.List;
import java.util.Locale;

/**
 * Writes a fixture type in the YAML format of the library, so it can be read back and edited by hand. Heads of a
 * multi-pixel fixture are written as a {@code heads} block when every head has the same channels in one block,
 * otherwise with {@code head} on each channel.
 */
final class FixtureTypeYamlWriter {

	private static final YAMLMapper MAPPER = YAMLMapper.builder()
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
			.build();

	private FixtureTypeYamlWriter() {
	}

	/** @param comment written as comment lines at the top, e.g. where the profile comes from */
	static String write(FixtureType type, String comment) {
		var document = new FixtureTypeDocument(type.id(), type.manufacturer(), type.model(), type.category().id(),
				physical(type), type.modes().stream().map(FixtureTypeYamlWriter::mode).toList());
		var header = comment.isBlank() ? "" : comment.lines().map(line -> "# " + line + "\n")
				.reduce("", String::concat);
		return header + MAPPER.writeValueAsString(document);
	}

	private static PhysicalDocument physical(FixtureType type) {
		var physical = type.physical();
		if (physical.panRange().isEmpty() && physical.tiltRange().isEmpty()) {
			return null;
		}
		return new PhysicalDocument(physical.panRange().isPresent() ? physical.panRange().getAsInt() : null,
				physical.tiltRange().isPresent() ? physical.tiltRange().getAsInt() : null);
	}

	private static ModeDocument mode(Mode mode) {
		var heads = mode.heads();
		if (heads == 0) {
			return new ModeDocument(mode.name(), channels(mode.channels(), false), null);
		}
		var whole = mode.channels().stream().filter(c -> c.head() == 0).toList();
		var headChannels = mode.channels().stream().filter(c -> c.head() > 0).toList();
		var perHead = headChannels.stream().filter(c -> c.head() == 1).toList();
		var regular = headChannels.size() == perHead.size() * heads;
		for (int i = 0; regular && i < headChannels.size(); i++) {
			var expected = perHead.get(i % perHead.size()).forHead(i / perHead.size() + 1);
			regular = headChannels.get(i).equals(expected);
		}
		var first = !mode.channels().isEmpty() && mode.channels().getFirst().head() > 0;
		var block = first
				? mode.channels().subList(0, headChannels.size())
				: mode.channels().subList(whole.size(), mode.channels().size());
		if (regular && block.equals(headChannels)) {
			return new ModeDocument(mode.name(), whole.isEmpty() ? null : channels(whole, false),
					new HeadsDocument(heads, first ? true : null, channels(perHead, false)));
		}
		return new ModeDocument(mode.name(), channels(mode.channels(), true), null);
	}

	private static List<ChannelDocument> channels(List<ChannelDefinition> channels, boolean withHead) {
		return channels.stream().map(channel -> channel(channel, withHead)).toList();
	}

	private static ChannelDocument channel(ChannelDefinition channel, boolean withHead) {
		return new ChannelDocument(
				channel.function().id(),
				channel.name().orElse(null),
				channel.resolution() == ChannelDefinition.RESOLUTION_16_BIT ? channel.resolution() : null,
				channel.defaultValue() != 0 ? channel.defaultValue() : null,
				channel.invert() ? true : null,
				channel.ranges().isEmpty() ? null : channel.ranges().stream()
						.map(range -> new RangeDocument(range.from(), range.to(), range.label(),
								range.color().orElse(null)))
						.toList(),
				channel.fadeBehavior().map(FixtureTypeYamlWriter::fade).orElse(null),
				withHead && channel.head() > 0 ? channel.head() : null);
	}

	private static String fade(FadeBehavior behavior) {
		return behavior.name().toLowerCase(Locale.ROOT);
	}
}
