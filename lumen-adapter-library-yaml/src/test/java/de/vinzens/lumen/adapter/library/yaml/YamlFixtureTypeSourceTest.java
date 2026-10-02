package de.vinzens.lumen.adapter.library.yaml;

import de.vinzens.lumen.application.library.port.out.SourcedFixtureType;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FadeBehavior;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.IssueSeverity;
import de.vinzens.lumen.domain.library.LibraryIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlFixtureTypeSourceTest {

	@TempDir
	Path dir;

	@Test
	void mapsAllFields() throws IOException {
		write("mh.yml", """
				id: spot
				manufacturer: Example
				model: Spot 60
				category: moving-head
				physical:
				  pan-range: 540
				  tilt-range: 270
				modes:
				  - name: 4ch
				    channels:
				      - function: pan
				        resolution: 16
				        default: 32768
				      - function: color-wheel
				        fade: smooth
				        ranges:
				          - { from: 0, to: 9, label: Weiß, color: "#ffffff" }
				      - function: generic
				        name: Lüfter
				        invert: true
				""");

		var result = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(result.issues()).isEmpty();
		var type = result.fixtureTypes().getFirst().fixtureType();
		assertThat(type.id()).isEqualTo("spot");
		assertThat(type.category()).isEqualTo(FixtureCategory.MOVING_HEAD);
		assertThat(type.physical().panRange()).hasValue(540);
		var mode = type.mode("4ch").orElseThrow();
		assertThat(mode.dmxChannelCount()).isEqualTo(4);
		var pan = mode.channels().get(0);
		assertThat(pan.function()).isEqualTo(ChannelFunction.PAN);
		assertThat(pan.defaultValue()).isEqualTo(32768);
		var wheel = mode.channels().get(1);
		assertThat(wheel.effectiveFadeBehavior()).isEqualTo(FadeBehavior.SMOOTH);
		assertThat(wheel.ranges()).singleElement().satisfies(range -> {
			assertThat(range.label()).isEqualTo("Weiß");
			assertThat(range.color()).contains("#ffffff");
		});
		assertThat(mode.channels().get(2).displayName()).isEqualTo("Lüfter");
		assertThat(mode.channels().get(2).invert()).isTrue();
	}

	@Test
	void loadsFilesFromSubdirectoriesWithRelativeSource() throws IOException {
		Files.createDirectories(dir.resolve("generic"));
		write("generic/dimmer.yaml", dimmer("dimmer"));
		write("readme.txt", "kein YAML");

		var result = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(result.fixtureTypes()).extracting(SourcedFixtureType::source)
				.containsExactly(Path.of("generic", "dimmer.yaml").toString());
	}

	@Test
	void reportsUnknownPropertyWithLine() throws IOException {
		write("typo.yml", dimmer("typo") + "adress: 1\n");

		var result = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(result.fixtureTypes()).isEmpty();
		assertThat(result.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.source()).isEqualTo("typo.yml");
			assertThat(issue.message()).startsWith("Unbekanntes Feld 'adress'").contains("Zeile");
		});
	}

	@Test
	void reportsUnknownFunctionAndCategory() throws IOException {
		write("bad.yml", """
				id: bad
				manufacturer: Generic
				model: Bad
				category: lamp
				modes:
				  - name: 1ch
				    channels:
				      - function: brightness
				""");

		var result = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(result.fixtureTypes()).isEmpty();
		assertThat(result.issues()).extracting(LibraryIssue::message)
				.anyMatch(m -> m.startsWith("Unbekannte category 'lamp'"))
				.anyMatch(m -> m.contains("Unbekannte function 'brightness'"));
		assertThat(result.issues()).allSatisfy(issue -> assertThat(issue.fixtureTypeId()).contains("bad"));
	}

	@Test
	void reportsBrokenAndEmptyFilesButKeepsLoadingOthers() throws IOException {
		write("a-broken.yml", "id: [unclosed\n");
		write("b-empty.yml", "");
		write("c-ok.yml", dimmer("ok"));

		var result = new YamlFixtureTypeSource(dir).loadAll();

		assertThat(result.fixtureTypes()).extracting(t -> t.fixtureType().id()).containsExactly("ok");
		assertThat(result.issues()).extracting(LibraryIssue::source).containsExactly("a-broken.yml", "b-empty.yml");
		assertThat(result.issues()).allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR));
	}

	@Test
	void missingDirectoryIsAWarning() {
		var result = new YamlFixtureTypeSource(dir.resolve("fehlt")).loadAll();

		assertThat(result.fixtureTypes()).isEmpty();
		assertThat(result.issues()).singleElement()
				.satisfies(issue -> assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING));
	}

	private void write(String name, String content) throws IOException {
		Files.writeString(dir.resolve(name), content);
	}

	private static String dimmer(String id) {
		return """
				id: %s
				manufacturer: Generic
				model: Dimmer
				category: dimmer
				modes:
				  - name: 1ch
				    channels:
				      - function: dimmer
				""".formatted(id);
	}
}
