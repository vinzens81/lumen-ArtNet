package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteSceneRepositoryTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute SPEED = Attribute.generic("Program speed");

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long par;
	private long dimmer;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(), new ArtNetAddress(0, 0, 0),
				new ArtNetTarget.Broadcast())).id();
		par = repos.fixtures().create(project, fixture("PAR", universe, 1)).id();
		dimmer = repos.fixtures().create(project, fixture("Dimmer", universe, 20)).id();
	}

	@Test
	void fiveScenesSurviveReopeningAndAreOrderedByNumber() {
		for (var number : new String[] {"3", "1", "2.5", "2", "10"}) {
			repos.scenes().create(project, spec(number, "Scene " + number),
					Map.of(par, new FixtureValues(Map.of(DIMMER, 0.5, SPEED, 0.25))));
		}

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));

		var scenes = reopened.scenes().findByProject(project);
		assertThat(scenes).extracting(s -> s.spec().number().toString()).containsExactly("1", "2", "2.5", "3", "10");
		assertThat(scenes.getFirst().values().get(par).values()).containsEntry(SPEED, 0.25);
	}

	@Test
	void specRoundTripIncludingAutoFollow() {
		var spec = new SceneSpec(SceneNumber.parse("4.5"), "Storm", Optional.of("GO on thunder"),
				Optional.of(Duration.ofSeconds(8)));

		var scene = repos.scenes().create(project, spec, Map.of());

		assertThat(repos.scenes().findById(scene.id())).map(Scene::spec).contains(spec);
		var updated = repos.scenes().updateSpec(scene.id(), spec("5", "Storm"));
		assertThat(updated.spec().autoFollow()).isEmpty();
	}

	@Test
	void timingRoundTripWithProjectTimesAsNull() {
		var flash = new SceneTiming(Optional.of(Duration.ZERO), Optional.empty(), Duration.ofMillis(1500));
		var spec = new SceneSpec(SceneNumber.parse("7"), "Flash", Optional.empty(), Optional.empty(), false, flash);

		var scene = repos.scenes().create(project, spec, Map.of());

		assertThat(repos.scenes().findById(scene.id())).map(s -> s.spec().timing()).contains(flash);
		var updated = repos.scenes().updateSpec(scene.id(), spec("7", "Flash"));
		assertThat(updated.spec().timing()).isEqualTo(SceneTiming.DEFAULT);
	}

	@Test
	void scriptRoundTrip() {
		var spec = new SceneSpec(SceneNumber.parse("1"), "Prologue", Optional.empty(), Optional.empty(), false,
				SceneTiming.DEFAULT, "Two households, both alike in dignity.\n\n**Enter Chorus**");

		var scene = repos.scenes().create(project, spec, Map.of());

		assertThat(repos.scenes().findById(scene.id())).map(Scene::spec).contains(spec);
		assertThat(repos.scenes().updateSpec(scene.id(), spec("1", "Prologue")).spec().script()).isEmpty();
	}

	@Test
	void replaceValuesOverwritesEverything() {
		var scene = repos.scenes().create(project, spec("1", "A"), Map.of(par, new FixtureValues(Map.of(DIMMER, 1.0))));

		var replaced = repos.scenes().replaceValues(scene.id(), Map.of(dimmer, new FixtureValues(Map.of(DIMMER, 0.3))));

		assertThat(replaced.values()).containsOnlyKeys(dimmer);
	}

	@Test
	void deletingAFixtureRemovesItFromScenes() {
		var scene = repos.scenes().create(project, spec("1", "A"), Map.of(
				par, new FixtureValues(Map.of(DIMMER, 1.0)),
				dimmer, new FixtureValues(Map.of(DIMMER, 1.0))));

		repos.fixtures().delete(par);

		assertThat(repos.scenes().findById(scene.id())).get()
				.satisfies(s -> assertThat(s.values()).containsOnlyKeys(dimmer));
	}

	@Test
	void numbersAreUniquePerProject() {
		repos.scenes().create(project, spec("2.50", "A"), Map.of());

		assertThatThrownBy(() -> repos.scenes().create(project, spec("2.5", "B"), Map.of()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void playbackStateSurvivesReopeningAndForgetsDeletedScenes() {
		var storm = repos.scenes().create(project, spec("4", "Storm"), Map.of()).id();
		assertThat(repos.playbackStates().currentScene(project)).isEmpty();

		repos.playbackStates().save(project, Optional.of(storm), Instant.EPOCH);
		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.playbackStates().currentScene(project)).contains(storm);

		reopened.playbackStates().save(project, Optional.empty(), Instant.EPOCH);
		assertThat(reopened.playbackStates().currentScene(project)).isEmpty();

		reopened.playbackStates().save(project, Optional.of(storm), Instant.EPOCH);
		reopened.scenes().delete(storm);
		assertThat(reopened.playbackStates().currentScene(project)).isEmpty();
	}

	@Test
	void stagePlanIsStoredPerProject() {
		var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
		assertThat(repos.stagePlans().find(project)).isEmpty();

		repos.stagePlans().save(project, new StagePlan("image/png", png));
		repos.stagePlans().save(project, new StagePlan("image/png", png));

		assertThat(repos.stagePlans().find(project)).contains(new StagePlan("image/png", png));
		repos.projects().delete(project);
		assertThat(repos.stagePlans().find(project)).isEmpty();
	}

	@Test
	void settingsAreKeyValuePairs() {
		assertThat(repos.settings().get("operating_mode")).isEmpty();
		repos.settings().put("operating_mode", "SHOW");
		repos.settings().put("operating_mode", "SETUP");
		assertThat(repos.settings().get("operating_mode")).contains("SETUP");
		repos.settings().remove("operating_mode");
		assertThat(repos.settings().get("operating_mode")).isEmpty();
	}

	private static SceneSpec spec(String number, String name) {
		return new SceneSpec(SceneNumber.parse(number), name, Optional.empty(), Optional.empty());
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universe, address, Optional.empty(), 0);
	}
}
