package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
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

	private static SceneSpec spec(String number, String name) {
		return new SceneSpec(SceneNumber.parse(number), name, Optional.empty(), Optional.empty());
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universe, address, Optional.empty(), 0);
	}
}
