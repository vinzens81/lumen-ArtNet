package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.Color;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.composition.Wave;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteCompositionRepositoryTest {

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long a;
	private long b;
	private long group;
	private long warm;
	private long cold;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		a = repos.fixtures().create(project, fixture("A", universe, 1)).id();
		b = repos.fixtures().create(project, fixture("B", universe, 2)).id();
		group = repos.groups().create(project, new GroupSpec("Ramp", Optional.empty(), 0, List.of(a, b))).id();
		warm = repos.looks().create(project, LookSpec.shared("Warm"), Look.EMPTY).id();
		cold = repos.looks().create(project, LookSpec.shared("Cold"), Look.EMPTY).id();
	}

	@Test
	void stepsWithDefaultAndOwnTimesSurviveReopening() {
		var spec = new CompositionSpec("Dusk", 1.5, 2, PlayMode.PING_PONG, IntensityMode.RELATIVE, Optional.empty(),
				"Evening");
		var steps = List.of(CompositionStep.of(warm), new CompositionStep(cold, Optional.of(0.0), Optional.of(4.25)),
				CompositionStep.parallelOf(warm));

		var created = repos.compositions().create(project, spec, steps);

		var loaded = new SqliteRepositories(SqliteDatabase.open(dbFile)).compositions().findById(created.id())
				.orElseThrow();
		assertThat(loaded.spec()).isEqualTo(spec);
		assertThat(loaded.steps()).isEqualTo(steps);
		assertThat(repos.compositions().findByProject(project)).singleElement().isEqualTo(loaded);
	}

	@Test
	void everyGeneratorSurvivesReopening() {
		List<Pattern> effects = List.of(
				new Chase(0.4, 2, Chase.Direction.PING_PONG, 0.3, 0.1, 0.9),
				new ColorCycle(List.of(new Color(1, 0, 0), new Color(0, 0.5, 1)), 2, 1, 0.5, 0.8),
				new Rainbow(10, 0.9, 1, 1),
				new Wave(Wave.Shape.SINE, 3, 0, 1, 0.25),
				new Flicker(Flicker.Character.FIRE, 0.6, 1.5, 42));
		for (var effect : effects) {
			var target = effect instanceof Chase ? new Target.Fixtures(List.of(b, a)) : new Target.Group(group);
			var settings = new GeneratorSettings(effect, target, effect instanceof Rainbow);
			var spec = new CompositionSpec(effect.kind().id(), 1, 1, PlayMode.LOOP, IntensityMode.ABSOLUTE,
					Optional.of(settings));
			repos.compositions().create(project, spec, List.of(CompositionStep.of(warm)));
		}

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.compositions().findByProject(project))
				.extracting(c -> c.spec().generator().orElseThrow().effect())
				.containsExactlyInAnyOrderElementsOf(effects);
		assertThat(reopened.compositions().findByProject(project))
				.filteredOn(c -> c.spec().name().equals("chase")).singleElement()
				.satisfies(c -> assertThat(c.spec().generator().orElseThrow().target())
						.isEqualTo(new Target.Fixtures(List.of(b, a))));
	}

	@Test
	void updateReplacesStepsAndGenerator() {
		var settings = new GeneratorSettings(new Rainbow(10, 1, 0, 1), new Target.Group(group), false);
		var created = repos.compositions().create(project, new CompositionSpec("R", 1, 1, PlayMode.LOOP,
				IntensityMode.ABSOLUTE, Optional.of(settings)), List.of(CompositionStep.of(warm), CompositionStep.of(cold)));

		var updated = repos.compositions().update(created.id(), CompositionSpec.of("Plain"),
				List.of(CompositionStep.of(cold)));

		assertThat(updated.spec().generator()).isEmpty();
		assertThat(updated.steps()).containsExactly(CompositionStep.of(cold));
	}

	@Test
	void aDeletedGroupLeavesTheGeneratorWithoutFixtures() {
		var settings = new GeneratorSettings(new Rainbow(10, 1, 0, 1), new Target.Group(group), false);
		var created = repos.compositions().create(project, new CompositionSpec("R", 1, 1, PlayMode.LOOP,
				IntensityMode.ABSOLUTE, Optional.of(settings)), List.of());

		repos.groups().delete(group);

		assertThat(repos.compositions().findById(created.id()).orElseThrow().spec().generator().orElseThrow().target())
				.isEqualTo(new Target.Fixtures(List.of()));
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-dimmer", "1ch", universe, address, Optional.empty(), 0);
	}

	@Test
	void compositionsQuickSetupMadeBeforeFoldersMoveToAutoGenerated() {
		var file = dir.resolve("before-folders.db");
		var config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		var dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("30").load().migrate();
		var jdbc = JdbcClient.create(dataSource);
		jdbc.sql("INSERT INTO project (id, name, active, created_at, updated_at) VALUES (1, 'Show', 1, '"
				+ Instant.EPOCH + "', '" + Instant.EPOCH + "')").update();
		jdbc.sql("INSERT INTO look (id, project_id, name) VALUES (1, 1, 'PAR – Red')").update();
		jdbc.sql("""
				INSERT INTO composition (id, project_id, name, fade_ms, hold_ms, play_mode, intensity_mode) VALUES
				  (1, 1, 'PAR – Red', 300, 1000, 'once', 'absolute'), (2, 1, 'PAR – 50 %', 300, 1000, 'once', 'absolute'),
				  (3, 1, 'Sunset – Red', 1000, 2000, 'loop', 'absolute'), (4, 1, 'PAR – 50 x', 300, 1000, 'once', 'absolute')
				""").update();
		jdbc.sql("""
				INSERT INTO composition_step (composition_id, step_index, look_id)
				VALUES (1, 0, 1), (2, 0, 1), (3, 0, 1), (4, 0, 1)""").update();

		var compositions = new SqliteRepositories(SqliteDatabase.open(file)).compositions().findByProject(1);

		assertThat(compositions).extracting(c -> c.spec().name() + ": " + c.spec().folder()).containsExactlyInAnyOrder(
				"PAR – Red: Auto generated", "PAR – 50 %: Auto generated", "Sunset – Red: Manual", "PAR – 50 x: Manual");
	}

	@Test
	void sharedLooksQuickSetupMadeBeforeFoldersMoveToAutoGenerated() {
		var file = dir.resolve("before-look-folders.db");
		var config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		var dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("31").load().migrate();
		var jdbc = JdbcClient.create(dataSource);
		jdbc.sql("INSERT INTO project (id, name, active, created_at, updated_at) VALUES (1, 'Show', 1, '"
				+ Instant.EPOCH + "', '" + Instant.EPOCH + "')").update();
		jdbc.sql("""
				INSERT INTO composition (id, project_id, name, fade_ms, hold_ms, play_mode, intensity_mode, folder) VALUES
				  (1, 1, 'PAR – Red', 300, 1000, 'once', 'absolute', 'Auto generated'),
				  (2, 1, 'Storm', 1000, 2000, 'loop', 'absolute', 'Manual')""").update();
		jdbc.sql("""
				INSERT INTO look (id, project_id, name, owner_composition_id) VALUES
				  (1, 1, 'PAR – Red', NULL), (2, 1, 'Storm', NULL), (3, 1, 'Other', NULL)""").update();
		jdbc.sql("INSERT INTO composition_step (composition_id, step_index, look_id) VALUES (1, 0, 1), (2, 0, 2)")
				.update();

		var looks = new SqliteRepositories(SqliteDatabase.open(file)).looks().findByProject(1);

		assertThat(looks).extracting(l -> l.spec().name() + ": " + l.spec().folder()).containsExactlyInAnyOrder(
				"PAR – Red: Auto generated", "Storm: Manual", "Other: Manual");
	}
}
