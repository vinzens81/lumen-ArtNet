package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.StagePlan;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteSceneRepositoryTest {

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
			create(spec(number, "Scene " + number));
		}

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));

		var scenes = reopened.scenes().findByProject(project);
		assertThat(scenes).extracting(s -> s.spec().number().toString()).containsExactly("1", "2", "2.5", "3", "10");
	}

	@Test
	void compositionsAndKeysOfASceneKeepTheirOrderAndSettings() {
		var a = composition("A");
		var b = composition("B");
		var uses = List.of(new SceneComposition(b, 0.5, 2, true, Optional.of(Duration.ofMillis(4500)),
				Optional.of(Duration.ZERO)), SceneComposition.of(a));
		var keys = List.of(new SceneKey(3, a, KeyMode.FLASH, Duration.ofMillis(500)),
				new SceneKey(1, b, KeyMode.TOGGLE, Duration.ZERO));

		var scene = repos.scenes().create(project, spec("1", "Fair"), uses, keys);

		var loaded = new SqliteRepositories(SqliteDatabase.open(dbFile)).scenes().findById(scene.id()).orElseThrow();
		assertThat(loaded.compositions()).isEqualTo(uses);
		assertThat(loaded.keys()).extracting(SceneKey::slot).containsExactly(1, 3);
		assertThat(loaded.keys()).contains(keys.getFirst());
		assertThat(repos.scenes().replaceCompositions(scene.id(), List.of(SceneComposition.of(a))).compositions())
				.extracting(SceneComposition::compositionId).containsExactly(a);
		assertThat(repos.scenes().replaceKeys(scene.id(), List.of()).keys()).isEmpty();
	}

	@Test
	void aCompositionPlayedByASceneCannotBeDeleted() {
		var a = composition("A");
		var scene = repos.scenes().create(project, spec("1", "Fair"), List.of(SceneComposition.of(a)), List.of());

		assertThatThrownBy(() -> repos.compositions().delete(a)).isInstanceOf(DataAccessException.class);
		repos.scenes().delete(scene.id());
		repos.compositions().delete(a);
		assertThat(repos.compositions().findById(a)).isEmpty();
	}

	@Test
	void specRoundTripIncludingAutoFollow() {
		var spec = new SceneSpec(SceneNumber.parse("4.5"), "Storm", Optional.of("GO on thunder"),
				Optional.of(Duration.ofSeconds(8)));

		var scene = create(spec);

		assertThat(repos.scenes().findById(scene.id())).map(Scene::spec).contains(spec);
		var updated = repos.scenes().updateSpec(scene.id(), spec("5", "Storm"));
		assertThat(updated.spec().autoFollow()).isEmpty();
	}

	@Test
	void timingRoundTripWithProjectTimesAsNull() {
		var flash = new SceneTiming(Optional.of(Duration.ZERO), Optional.empty(), Duration.ofMillis(1500));
		var spec = new SceneSpec(SceneNumber.parse("7"), "Flash", Optional.empty(), Optional.empty(), flash);

		var scene = create(spec);

		assertThat(repos.scenes().findById(scene.id())).map(s -> s.spec().timing()).contains(flash);
		var updated = repos.scenes().updateSpec(scene.id(), spec("7", "Flash"));
		assertThat(updated.spec().timing()).isEqualTo(SceneTiming.DEFAULT);
	}

	@Test
	void scriptRoundTrip() {
		var spec = new SceneSpec(SceneNumber.parse("1"), "Prologue", Optional.empty(), Optional.empty(),
				SceneTiming.DEFAULT, "Two households, both alike in dignity.\n\n**Enter Chorus**");

		var scene = create(spec);

		assertThat(repos.scenes().findById(scene.id())).map(Scene::spec).contains(spec);
		assertThat(repos.scenes().updateSpec(scene.id(), spec("1", "Prologue")).spec().script()).isEmpty();
	}

	@Test
	void numbersAreUniquePerProject() {
		create(spec("2.50", "A"));

		assertThatThrownBy(() -> create(spec("2.5", "B")))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void playbackStateSurvivesReopeningAndForgetsDeletedScenes() {
		var storm = create(spec("4", "Storm")).id();
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

	private Scene create(SceneSpec spec) {
		return repos.scenes().create(project, spec, List.of(), List.of());
	}

	private long composition(String name) {
		return repos.compositions().create(project, CompositionSpec.of(name), List.of()).id();
	}

	private static SceneSpec spec(String number, String name) {
		return new SceneSpec(SceneNumber.parse(number), name, Optional.empty(), Optional.empty());
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universe, address, Optional.empty(), 0);
	}
}
