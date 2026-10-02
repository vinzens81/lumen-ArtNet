package de.vinzens.lumen.domain.library;

import de.vinzens.lumen.domain.IssueSeverity;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureTypeValidatorTest {

	private static final String SOURCE = "test.yml";

	@Test
	void validTypeHasNoIssues() {
		var type = type("rgb-par", mode("3ch", channel(ChannelFunction.RED), channel(ChannelFunction.GREEN),
				channel(ChannelFunction.BLUE)));

		assertThat(FixtureTypeValidator.validate(type, SOURCE)).isEmpty();
	}

	@Test
	void rejectsInvalidId() {
		var type = type("RGB Par", mode("1ch", channel(ChannelFunction.DIMMER)));

		assertThat(messages(type)).singleElement().asString().contains("Invalid id");
	}

	@Test
	void requiresAtLeastOneModeAndChannel() {
		assertThat(messages(type("empty"))).containsExactly("At least one mode is required");
		assertThat(messages(type("no-channels", mode("0ch"))))
				.containsExactly("Mode '0ch': at least one channel is required");
	}

	@Test
	void rejectsDuplicateModeNames() {
		var type = type("dup", mode("1ch", channel(ChannelFunction.DIMMER)), mode("1ch", channel(ChannelFunction.DIMMER)));

		assertThat(messages(type)).containsExactly("Mode '1ch' is defined twice");
	}

	@Test
	void genericChannelNeedsName() {
		var type = type("gen", mode("1ch", channel(ChannelFunction.GENERIC)));

		assertThat(messages(type)).singleElement().asString().contains("generic channels need a name");
	}

	@Test
	void sixteenBitChannelAllowsLargeDefaultAndCountsTwice() {
		var pan = new ChannelDefinition(ChannelFunction.PAN, Optional.empty(), 16, 32_768, false, List.of(),
				Optional.empty());
		var mode = mode("2ch", pan);

		assertThat(FixtureTypeValidator.validate(type("mh", mode), SOURCE)).isEmpty();
		assertThat(mode.dmxChannelCount()).isEqualTo(2);
	}

	@Test
	void rejectsInvalidResolutionAndDefault() {
		var badResolution = new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 12, 0, false, List.of(),
				Optional.empty());
		var badDefault = new ChannelDefinition(ChannelFunction.RED, Optional.empty(), 8, 300, false, List.of(),
				Optional.empty());

		assertThat(messages(type("bad", mode("2ch", badResolution, badDefault))))
				.anyMatch(m -> m.contains("resolution must be 8 or 16"))
				.anyMatch(m -> m.contains("default must be between 0 and 255"));
	}

	@Test
	void rejectsOverlappingAndInvalidRanges() {
		var strobe = new ChannelDefinition(ChannelFunction.STROBE, Optional.empty(), 8, 0, false, List.of(
				new ChannelRange(0, 10, "Off", Optional.empty()),
				new ChannelRange(10, 255, "Strobe", Optional.empty()),
				new ChannelRange(20, 5, "Broken", Optional.of("red"))), Optional.empty());

		assertThat(messages(type("strobe", mode("1ch", strobe))))
				.anyMatch(m -> m.contains("overlap"))
				.anyMatch(m -> m.contains("from ≤ to"))
				.anyMatch(m -> m.contains("format #rrggbb"));
	}

	@Test
	void duplicateFunctionIsOnlyAWarning() {
		var type = type("twice", mode("2ch", channel(ChannelFunction.DIMMER), channel(ChannelFunction.DIMMER)));

		var issues = FixtureTypeValidator.validate(type, SOURCE);

		assertThat(issues).singleElement().satisfies(issue -> {
			assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);
			assertThat(issue.message()).contains("more than once");
		});
	}

	@Test
	void rejectsModesLargerThanAnUniverse() {
		var channels = Collections.nCopies(257,
				new ChannelDefinition(ChannelFunction.GENERIC, Optional.of("x"), 16, 0, false, List.of(),
						Optional.empty()));

		assertThat(messages(type("huge", new Mode("514ch", channels))))
				.anyMatch(m -> m.contains("occupies 514 channels"));
	}

	private static List<String> messages(FixtureType type) {
		return FixtureTypeValidator.validate(type, SOURCE).stream().map(LibraryIssue::message).toList();
	}

	private static FixtureType type(String id, Mode... modes) {
		return new FixtureType(id, "Generic", "Test", FixtureCategory.PAR, PhysicalProperties.NONE, List.of(modes));
	}

	private static Mode mode(String name, ChannelDefinition... channels) {
		return new Mode(name, List.of(channels));
	}

	private static ChannelDefinition channel(ChannelFunction function) {
		return new ChannelDefinition(function, Optional.empty(), 8, 0, false, List.of(), Optional.empty());
	}
}
