package de.vinzens.lumen.application.library;

import de.vinzens.lumen.application.library.port.out.LoadResult;
import de.vinzens.lumen.application.library.port.out.SourcedFixtureType;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureLibraryServiceTest {

	@Test
	void loadsValidTypesSortedByManufacturerAndModel() {
		var service = serviceWith(new LoadResult(List.of(
				sourced(type("b-par", "Zeta", "Par"), "b.yml"),
				sourced(type("a-par", "Alpha", "Par"), "a.yml")), List.of()));

		assertThat(service.fixtureTypes()).extracting(FixtureType::id).containsExactly("a-par", "b-par");
		assertThat(service.fixtureType("b-par")).isPresent();
		assertThat(service.issues()).isEmpty();
	}

	@Test
	void skipsInvalidTypesAndReportsIssues() {
		var invalid = new FixtureType("broken", "Generic", "Broken", FixtureCategory.PAR, PhysicalProperties.NONE,
				List.of());
		var service = serviceWith(new LoadResult(List.of(sourced(invalid, "broken.yml")),
				List.of(LibraryIssue.error("other.yml", null, "YAML kaputt"))));

		assertThat(service.fixtureTypes()).isEmpty();
		assertThat(service.issues()).extracting(LibraryIssue::source).containsExactly("other.yml", "broken.yml");
	}

	@Test
	void keepsFirstDefinitionOfDuplicateId() {
		var service = serviceWith(new LoadResult(List.of(
				sourced(type("par", "Generic", "Erster"), "a.yml"),
				sourced(type("par", "Generic", "Zweiter"), "b.yml")), List.of()));

		assertThat(service.fixtureType("par")).map(FixtureType::model).contains("Erster");
		assertThat(service.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.source()).isEqualTo("b.yml");
			assertThat(issue.message()).contains("bereits in a.yml definiert");
		});
	}

	private static FixtureLibraryService serviceWith(LoadResult result) {
		var service = new FixtureLibraryService(() -> result);
		service.reload();
		return service;
	}

	private static SourcedFixtureType sourced(FixtureType type, String source) {
		return new SourcedFixtureType(type, source);
	}

	private static FixtureType type(String id, String manufacturer, String model) {
		var dimmer = new ChannelDefinition(ChannelFunction.DIMMER, Optional.empty(), 8, 0, false, List.of(),
				Optional.empty());
		return new FixtureType(id, manufacturer, model, FixtureCategory.PAR, PhysicalProperties.NONE,
				List.of(new Mode("1ch", List.of(dimmer))));
	}
}
