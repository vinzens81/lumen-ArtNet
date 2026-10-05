package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyButton;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SqlitePartyButtonRepositoryTest {

	private static final Attribute GOBO = Attribute.of(ChannelFunction.GOBO);
	private static final Attribute SPEED = Attribute.generic("Program speed");

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long spot;
	private long par;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Party", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		spot = repos.fixtures().create(project, fixture("Spot", universe, 1)).id();
		par = repos.fixtures().create(project, fixture("PAR", universe, 20)).id();
	}

	@Test
	void buttonsSurviveReopeningInTheirOrder() {
		var buttons = repos.partyButtons();
		var gobo = buttons.create(project, "Gobo 3", KeyMode.TOGGLE, Map.of(spot, new FixtureValues(Map.of(GOBO, 0.3))));
		var strobe = buttons.create(project, " Strobe ", KeyMode.FLASH,
				Map.of(par, new FixtureValues(Map.of(SPEED, 0.9))));

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile)).partyButtons().findByProject(project);

		assertThat(reopened).extracting(PartyButton::name).containsExactly("Gobo 3", "Strobe");
		assertThat(reopened.get(1).mode()).isEqualTo(KeyMode.FLASH);
		assertThat(reopened.get(1).values().get(par).get(SPEED)).contains(0.9);

		buttons.reorder(List.of(strobe.id(), gobo.id()));
		assertThat(buttons.findByProject(project)).extracting(PartyButton::id).containsExactly(strobe.id(), gobo.id());
	}

	@Test
	void updateReplaceValuesAndDelete() {
		var buttons = repos.partyButtons();
		var button = buttons.create(project, "Gobo", KeyMode.TOGGLE, Map.of(spot, new FixtureValues(Map.of(GOBO, 0.3))));

		assertThat(buttons.update(button.id(), "Prism", KeyMode.FLASH).name()).isEqualTo("Prism");
		var replaced = buttons.replaceValues(button.id(), Map.of(par, new FixtureValues(Map.of(GOBO, 0.5))));
		assertThat(replaced.values()).containsOnlyKeys(par);

		repos.fixtures().delete(par);
		assertThat(buttons.findById(button.id()).orElseThrow().values()).as("fixture deleted").isEmpty();

		buttons.delete(button.id());
		assertThat(buttons.findByProject(project)).isEmpty();
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universe, address, Optional.empty(), 0);
	}
}
