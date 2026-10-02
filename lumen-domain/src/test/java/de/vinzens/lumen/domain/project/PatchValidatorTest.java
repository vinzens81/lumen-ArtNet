package de.vinzens.lumen.domain.project;

import de.vinzens.lumen.domain.IssueSeverity;
import de.vinzens.lumen.domain.library.ChannelDefinition;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.library.FixtureCategory;
import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.Mode;
import de.vinzens.lumen.domain.library.PhysicalProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatchValidatorTest {

	private static final FixtureType PAR = new FixtureType("par", "Generic", "Par", FixtureCategory.PAR,
			PhysicalProperties.NONE, List.of(new Mode("4ch", Collections.nCopies(4, channel()))));
	private static final Map<String, FixtureType> LIBRARY = Map.of("par", PAR);

	private static final Universe U1 = universe(1, 1, 0);
	private static final Universe U2 = universe(2, 2, 1);

	@Test
	void validPatchHasNoIssues() {
		var fixtures = List.of(fixture(10, "Front left", U1, 1), fixture(11, "Front right", U1, 5),
				fixture(12, "Back", U2, 1));

		assertThat(validate(List.of(U1, U2), fixtures)).isEmpty();
	}

	@Test
	void reportsOverlapWithBothFixtures() {
		var fixtures = List.of(fixture(10, "Front left", U1, 1), fixture(11, "Front right", U1, 3));

		assertThat(validate(List.of(U1), fixtures)).singleElement().satisfies(issue -> {
			assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
			assertThat(issue.fixtureIds()).containsExactly(10L, 11L);
			assertThat(issue.message()).isEqualTo(
					"'Front left' and 'Front right' overlap in Universe 1 – U1 (channels 3–4)");
		});
	}

	@Test
	void sameAddressInDifferentUniversesIsFine() {
		assertThat(validate(List.of(U1, U2), List.of(fixture(10, "A", U1, 1), fixture(11, "B", U2, 1)))).isEmpty();
	}

	@Test
	void reportsFixturesExceedingTheUniverse() {
		assertThat(validate(List.of(U1), List.of(fixture(10, "Last", U1, 510))))
				.singleElement()
				.satisfies(issue -> assertThat(issue.message()).contains("occupies channels 510–513"));
	}

	@Test
	void reportsUnknownTypeModeAndUniverse() {
		var unknownType = new PatchedFixture(10, 1, spec("Hazer", "fog-machine", "1ch", U1.id(), 1));
		var unknownMode = new PatchedFixture(11, 1, spec("Par", "par", "8ch", U1.id(), 10));
		var unknownUniverse = new PatchedFixture(12, 1, spec("Lost", "par", "4ch", 99, 20));

		assertThat(validate(List.of(U1), List.of(unknownType, unknownMode, unknownUniverse)))
				.extracting(PatchIssue::message)
				.containsExactly(
						"'Hazer': fixture type 'fog-machine' is not in the fixture library",
						"'Par': fixture type 'par' has no mode '8ch'",
						"'Lost': universe does not exist");
	}

	@Test
	void warnsAboutDuplicateArtNetAddress() {
		var duplicate = universe(3, 3, 0);

		assertThat(validate(List.of(U1, duplicate), List.of())).singleElement().satisfies(issue -> {
			assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);
			assertThat(issue.universeIds()).containsExactly(1L, 3L);
		});
	}

	private static List<PatchIssue> validate(List<Universe> universes, List<PatchedFixture> fixtures) {
		return PatchValidator.validate(universes, fixtures, id -> Optional.ofNullable(LIBRARY.get(id)));
	}

	private static Universe universe(long id, int number, int artNetUniverse) {
		return new Universe(id, 1, new UniverseSpec(number, Optional.of("U" + number),
				new ArtNetAddress(0, 0, artNetUniverse), new ArtNetTarget.Broadcast()));
	}

	private static PatchedFixture fixture(long id, String name, Universe universe, int address) {
		return new PatchedFixture(id, 1, spec(name, "par", "4ch", universe.id(), address));
	}

	private static FixtureSpec spec(String name, String type, String mode, long universeId, int address) {
		return new FixtureSpec(name, Optional.empty(), type, mode, universeId, address, Optional.empty(), 0);
	}

	private static ChannelDefinition channel() {
		return new ChannelDefinition(ChannelFunction.GENERIC, Optional.of("x"), 8, 0, false, List.of(),
				Optional.empty());
	}
}
