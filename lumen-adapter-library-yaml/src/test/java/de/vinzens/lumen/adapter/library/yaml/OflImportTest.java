package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureTypeValidator;
import de.vinzens.lumen.domain.library.LibraryIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OflImportTest {

	@TempDir
	Path dir;

	@Test
	void convertsAMovingHead() throws IOException {
		var converted = OflConverter.convert(resource("moving-head.json"), "Acme Lighting");
		var type = converted.fixtureType();

		assertThat(type.id()).isEqualTo("acme-lighting-spot-230-pro");
		assertThat(type.category()).isEqualTo(FixtureCategory.MOVING_HEAD);
		assertThat(type.physical().panRange()).hasValue(540);
		var standard = type.mode("11ch").orElseThrow();
		assertThat(standard.dmxChannelCount()).isEqualTo(11);
		assertThat(standard.channels()).extracting(c -> c.function())
				.containsExactly(ChannelFunction.PAN, ChannelFunction.TILT, ChannelFunction.SPEED,
						ChannelFunction.DIMMER, ChannelFunction.STROBE, ChannelFunction.COLOR_WHEEL,
						ChannelFunction.GOBO, ChannelFunction.GENERIC, ChannelFunction.RESET);
		assertThat(standard.channels().getFirst().resolution()).isEqualTo(16);
		assertThat(standard.channels().get(4).defaultValue()).isEqualTo(255);
		assertThat(standard.channels().get(7).name()).as("second speed channel").contains("Speed");
		var wheel = standard.channels().get(5);
		assertThat(wheel.ranges()).hasSize(4);
		assertThat(wheel.ranges().get(1).label()).isEqualTo("Red");
		assertThat(wheel.ranges().get(1).color()).contains("#ff0000");
		assertThat(wheel.ranges().getFirst().color()).as("open slot").contains("#ffffff");
		assertThat(standard.channels().get(6).ranges().get(1).label()).isEqualTo("Dots");

		var basic = type.mode("8ch").orElseThrow();
		assertThat(basic.dmxChannelCount()).isEqualTo(8);
		assertThat(basic.channels().get(5).name()).contains("Unused");
		assertThat(basic.channels().get(6).name()).as("fine channel without its coarse one").contains("Tilt fine");
		assertThat(converted.warnings()).anyMatch(w -> w.contains("Tilt fine"))
				.anyMatch(w -> w.contains("Color Wheel") && w.contains("WheelRotation"))
				.doesNotHaveDuplicates();
		assertThat(FixtureTypeValidator.validate(type, "test")).noneMatch(LibraryIssue::isError);
	}

	@Test
	void convertsPixelsToHeads() throws IOException {
		var type = OflConverter.convert(resource("pixel-bar.json"), "Acme").fixtureType();

		var perPixel = type.mode("13ch").orElseThrow();
		assertThat(perPixel.heads()).isEqualTo(4);
		assertThat(perPixel.channels()).extracting(c -> c.attribute().id()).startsWith("dimmer", "red@1", "green@1",
				"blue@1", "red@2");
		var perChannel = type.mode("12ch").orElseThrow();
		assertThat(perChannel.channels()).extracting(c -> c.attribute().id()).startsWith("red@1", "red@2", "red@3",
				"red@4", "green@1");
		assertThat(type.category()).isEqualTo(FixtureCategory.BAR);
	}

	@Test
	void writtenProfilesReadBackTheSame() throws IOException {
		var files = new YamlFixtureTypeFiles(dir);
		for (var name : new String[] {"moving-head.json", "pixel-bar.json"}) {
			var type = files.convertOfl(resource(name), "Acme").fixtureType();
			files.save(type, "acme/" + type.id() + ".yml", "Imported for a test");
		}

		var loaded = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(loaded.issues()).isEmpty();
		assertThat(loaded.fixtureTypes()).hasSize(2);
		for (var sourced : loaded.fixtureTypes()) {
			var original = files.convertOfl(resource(sourced.fixtureType().model().startsWith("Pixel")
					? "pixel-bar.json" : "moving-head.json"), "Acme").fixtureType();
			assertThat(sourced.fixtureType()).isEqualTo(original);
		}
		var yaml = java.nio.file.Files.readString(dir.resolve("acme/acme-pixel-bar-4.yml"));
		assertThat(yaml).startsWith("# Imported for a test").contains("heads:").contains("count: 4");
	}

	@Test
	void rejectsOtherFilesAndPathsOutsideTheLibrary() {
		assertThatThrownBy(() -> OflConverter.convert("{\"hello\": 1}", "Acme"))
				.isInstanceOf(DomainValidationException.class).hasMessageContaining("Open Fixture Library");
		assertThatThrownBy(() -> OflConverter.convert("not json", "Acme"))
				.isInstanceOf(DomainValidationException.class);
		assertThatThrownBy(() -> new YamlFixtureTypeFiles(dir).exists("../outside.yml"))
				.isInstanceOf(DomainValidationException.class);
	}

	private static String resource(String name) throws IOException {
		try (var in = OflImportTest.class.getResourceAsStream("/ofl/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
