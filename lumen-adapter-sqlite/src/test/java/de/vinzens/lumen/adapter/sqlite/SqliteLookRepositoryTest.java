package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.library.ChannelFunction;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.preset.PresetSpec;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteLookRepositoryTest {

	private static final Attribute DIMMER = Attribute.of(ChannelFunction.DIMMER);
	private static final Attribute RED = Attribute.of(ChannelFunction.RED);
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
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		par = repos.fixtures().create(project, fixture("PAR", 1, universe)).id();
		dimmer = repos.fixtures().create(project, fixture("Dimmer", 20, universe)).id();
	}

	@Test
	void looksSurviveReopeningWithTheirValuesAndPresets() {
		var warm = repos.presets().create(project, new PresetSpec("Warm", AttributeGroup.COLOR),
				Map.of(par, new FixtureValues(Map.of(RED, 0.9))));
		var content = new Look(Map.of(par, new FixtureValues(Map.of(DIMMER, 1.0, RED, 0.9, SPEED, 0.25))),
				PresetRefs.NONE.with(par, AttributeGroup.COLOR, warm.id()));

		var look = repos.looks().create(project, LookSpec.shared("Warm front"), content);

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.looks().findByProject(project)).singleElement().satisfies(loaded -> {
			assertThat(loaded.id()).isEqualTo(look.id());
			assertThat(loaded.spec()).isEqualTo(LookSpec.shared("Warm front"));
			assertThat(loaded.look()).isEqualTo(content);
		});

		repos.presets().delete(warm.id());
		var after = repos.looks().findById(look.id()).orElseThrow().look();
		assertThat(after.presets().isEmpty()).isTrue();
		assertThat(after.values()).as("values stay").isEqualTo(content.values());
	}

	@Test
	void replacingTheContentOverwritesEverything() {
		var look = repos.looks().create(project, LookSpec.shared("A"), lit(par, 1));

		var replaced = repos.looks().replaceLook(look.id(), lit(dimmer, 0.3));

		assertThat(replaced.look().values()).containsOnlyKeys(dimmer);
		assertThat(repos.looks().updateSpec(look.id(), LookSpec.shared("B")).spec().name()).isEqualTo("B");
	}

	@Test
	void revisionsKeepOnlyTheNewest() {
		var look = repos.looks().create(project, LookSpec.shared("Look"), Look.EMPTY);
		for (var level : new double[] {0.1, 0.2, 0.3}) {
			repos.looks().saveRevision(look.id(), Instant.EPOCH, "Update", lit(par, level), 2);
		}

		var revisions = repos.looks().findRevisions(look.id());
		assertThat(revisions).extracting(r -> r.look().values().get(par).get(DIMMER).orElseThrow())
				.containsExactly(0.3, 0.2);
		assertThat(repos.looks().findRevision(revisions.getFirst().id())).get()
				.satisfies(r -> assertThat(r.reason()).isEqualTo("Update"));

		repos.looks().delete(look.id());
		assertThat(repos.looks().findRevisions(look.id())).isEmpty();
	}

	@Test
	void deletingAFixtureRemovesItFromLooks() {
		var look = repos.looks().create(project, LookSpec.shared("A"),
				Look.of(Map.of(par, values(1), dimmer, values(1))));

		repos.fixtures().delete(par);

		assertThat(repos.looks().findById(look.id()).orElseThrow().look().values()).containsOnlyKeys(dimmer);
	}

	@Test
	void ownLooksGoWithTheirCompositionAndPlayedLooksStay() {
		var composition = repos.compositions().create(project, CompositionSpec.of("Chase"), List.of());
		var own = repos.looks().create(project, new LookSpec("Step 1", Optional.of(composition.id())), lit(par, 1));
		var shared = repos.looks().create(project, LookSpec.shared("Warm"), lit(dimmer, 1));
		repos.compositions().update(composition.id(), composition.spec(),
				List.of(CompositionStep.of(own.id()), CompositionStep.of(shared.id())));

		assertThatThrownBy(() -> repos.looks().delete(shared.id())).as("still played")
				.isInstanceOf(DataAccessException.class);
		repos.compositions().delete(composition.id());

		assertThat(repos.looks().findById(own.id())).isEmpty();
		assertThat(repos.looks().findById(shared.id())).isPresent();
	}

	private static Look lit(long fixtureId, double level) {
		return Look.of(Map.of(fixtureId, values(level)));
	}

	private static FixtureValues values(double level) {
		return new FixtureValues(Map.of(DIMMER, level));
	}

	private static FixtureSpec fixture(String name, int address, long universe) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universe, address, Optional.empty(),
				0);
	}
}
