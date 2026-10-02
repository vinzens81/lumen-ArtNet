package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.Project;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteRepositoriesTest {

	private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;

	@BeforeEach
	void openDatabase() {
		dbFile = dir.resolve("sub/lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
	}

	@Test
	void createsDatabaseFileAndParentDirectory() {
		assertThat(dbFile).exists();
	}

	@Test
	void projectRoundTripAndUpdate() {
		var created = repos.projects().create(projectSpec("The Tempest"), NOW);

		assertThat(created.spec().fadeIn()).isEqualTo(Duration.ofSeconds(2));
		assertThat(created.spec().venue()).contains("City Theatre");
		assertThat(created.active()).isFalse();

		var later = NOW.plusSeconds(60);
		var updated = repos.projects().update(created.id(),
				new ProjectSpec("The Tempest (guest performance)", Optional.empty(), Duration.ZERO, Duration.ofSeconds(5)), later);

		assertThat(updated.spec().name()).isEqualTo("The Tempest (guest performance)");
		assertThat(updated.createdAt()).isEqualTo(NOW);
		assertThat(updated.updatedAt()).isEqualTo(later);
	}

	@Test
	void onlyOneProjectIsActive() {
		var a = repos.projects().create(projectSpec("A"), NOW);
		var b = repos.projects().create(projectSpec("B"), NOW);

		repos.projects().activate(a.id());
		repos.projects().activate(b.id());

		assertThat(repos.projects().findAll()).filteredOn(Project::active)
				.extracting(Project::id).containsExactly(b.id());
	}

	@Test
	void dataSurvivesReopeningTheDatabase() {
		var project = repos.projects().create(projectSpec("Persistent"), NOW);
		var universe = repos.universes().create(project.id(), universeSpec(1));
		repos.fixtures().create(project.id(), fixtureSpec("PAR left", universe.id(), 1));

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));

		assertThat(reopened.projects().findById(project.id())).isPresent();
		assertThat(reopened.fixtures().findByProject(project.id()))
				.singleElement()
				.satisfies(f -> {
					assertThat(f.spec().name()).isEqualTo("PAR left");
					assertThat(f.spec().position()).contains(new Position(0.25, 0.75));
				});
	}

	@Test
	void fixtureWithoutPositionAndShortName() {
		var project = repos.projects().create(projectSpec("P"), NOW);
		var universe = repos.universes().create(project.id(), universeSpec(1));
		var spec = new FixtureSpec("Dimmer 1", Optional.empty(), "generic-dimmer", "1ch", universe.id(), 1,
				Optional.empty(), 0);

		var fixture = repos.fixtures().create(project.id(), spec);

		assertThat(repos.fixtures().findById(fixture.id())).get()
				.satisfies(f -> assertThat(f.spec()).isEqualTo(spec));
	}

	@Test
	void universeRoundTrip() {
		var project = repos.projects().create(projectSpec("P"), NOW);
		var unicast = new UniverseSpec(2, Optional.of("Stage"), new ArtNetAddress(0, 1, 2),
				ArtNetTarget.parse("192.168.1.50"));

		var universe = repos.universes().create(project.id(), unicast);

		assertThat(repos.universes().findById(universe.id())).get()
				.satisfies(u -> assertThat(u.spec()).isEqualTo(unicast));
	}

	@Test
	void universeInUseCannotBeDeletedOnDatabaseLevel() {
		var project = repos.projects().create(projectSpec("P"), NOW);
		var universe = repos.universes().create(project.id(), universeSpec(1));
		repos.fixtures().create(project.id(), fixtureSpec("PAR", universe.id(), 1));

		assertThat(repos.fixtures().countByUniverse(universe.id())).isEqualTo(1);
		assertThatThrownBy(() -> repos.universes().delete(universe.id()))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("FOREIGN KEY constraint failed");
	}

	@Test
	void groupKeepsMemberOrderAndLosesDeletedFixtures() {
		var project = repos.projects().create(projectSpec("P"), NOW);
		var universe = repos.universes().create(project.id(), universeSpec(1));
		var a = repos.fixtures().create(project.id(), fixtureSpec("A", universe.id(), 1));
		var b = repos.fixtures().create(project.id(), fixtureSpec("B", universe.id(), 10));
		var group = repos.groups().create(project.id(),
				new GroupSpec("Front", Optional.of("#ff9a3c"), 0, List.of(b.id(), a.id())));

		assertThat(group.spec().fixtureIds()).containsExactly(b.id(), a.id());

		repos.fixtures().delete(b.id());

		assertThat(repos.groups().findById(group.id())).get()
				.satisfies(g -> assertThat(g.spec().fixtureIds()).containsExactly(a.id()));
	}

	@Test
	void deletingAProjectRemovesEverything() {
		var project = repos.projects().create(projectSpec("Doomed"), NOW);
		var universe = repos.universes().create(project.id(), universeSpec(1));
		var fixture = repos.fixtures().create(project.id(), fixtureSpec("PAR", universe.id(), 1));
		var group = repos.groups().create(project.id(), new GroupSpec("G", Optional.empty(), 0, List.of(fixture.id())));

		repos.projects().delete(project.id());

		assertThat(repos.universes().findById(universe.id())).isEmpty();
		assertThat(repos.fixtures().findById(fixture.id())).isEmpty();
		assertThat(repos.groups().findById(group.id())).isEmpty();
	}

	private static ProjectSpec projectSpec(String name) {
		return new ProjectSpec(name, Optional.of("City Theatre"), Duration.ofSeconds(2), Duration.ofSeconds(3));
	}

	private static UniverseSpec universeSpec(int number) {
		return new UniverseSpec(number, Optional.empty(), new ArtNetAddress(0, 0, number - 1),
				new ArtNetTarget.Broadcast());
	}

	private static FixtureSpec fixtureSpec(String name, long universeId, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "8ch", universeId, address,
				Optional.of(new Position(0.25, 0.75)), 0);
	}
}
