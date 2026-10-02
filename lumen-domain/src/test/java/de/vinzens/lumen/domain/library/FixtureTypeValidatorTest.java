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

		assertThat(messages(type)).singleElement().asString().contains("Ungültige id");
	}

	@Test
	void requiresAtLeastOneModeAndChannel() {
		assertThat(messages(type("empty"))).containsExactly("Mindestens ein Modus ist erforderlich");
		assertThat(messages(type("no-channels", mode("0ch"))))
				.containsExactly("Modus '0ch': Mindestens ein Kanal ist erforderlich");
	}

	@Test
	void rejectsDuplicateModeNames() {
		var type = type("dup", mode("1ch", channel(ChannelFunction.DIMMER)), mode("1ch", channel(ChannelFunction.DIMMER)));

		assertThat(messages(type)).containsExactly("Modus '1ch' ist doppelt definiert");
	}

	@Test
	void genericChannelNeedsName() {
		var type = type("gen", mode("1ch", channel(ChannelFunction.GENERIC)));

		assertThat(messages(type)).singleElement().asString().contains("generic-Kanäle brauchen einen name");
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
				.anyMatch(m -> m.contains("resolution muss 8 oder 16 sein"))
				.anyMatch(m -> m.contains("default muss zwischen 0 und 255 liegen"));
	}

	@Test
	void rejectsOverlappingAndInvalidRanges() {
		var strobe = new ChannelDefinition(ChannelFunction.STROBE, Optional.empty(), 8, 0, false, List.of(
				new ChannelRange(0, 10, "Aus", Optional.empty()),
				new ChannelRange(10, 255, "Strobe", Optional.empty()),
				new ChannelRange(20, 5, "Kaputt", Optional.of("rot"))), Optional.empty());

		assertThat(messages(type("strobe", mode("1ch", strobe))))
				.anyMatch(m -> m.contains("überlappen"))
				.anyMatch(m -> m.contains("from ≤ to"))
				.anyMatch(m -> m.contains("Format #rrggbb"));
	}

	@Test
	void duplicateFunctionIsOnlyAWarning() {
		var type = type("twice", mode("2ch", channel(ChannelFunction.DIMMER), channel(ChannelFunction.DIMMER)));

		var issues = FixtureTypeValidator.validate(type, SOURCE);

		assertThat(issues).singleElement().satisfies(issue -> {
			assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);
			assertThat(issue.message()).contains("mehrfach");
		});
	}

	@Test
	void rejectsModesLargerThanAnUniverse() {
		var channels = Collections.nCopies(257,
				new ChannelDefinition(ChannelFunction.GENERIC, Optional.of("x"), 16, 0, false, List.of(),
						Optional.empty()));

		assertThat(messages(type("huge", new Mode("514ch", channels))))
				.anyMatch(m -> m.contains("belegt 514 Kanäle"));
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
