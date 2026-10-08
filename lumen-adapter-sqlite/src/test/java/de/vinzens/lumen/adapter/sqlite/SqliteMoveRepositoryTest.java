package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveRepeat;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.move.OnceStart;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.project.UniverseSpec;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteMoveRepositoryTest {

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long head1;
	private long head2;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		head1 = repos.fixtures().create(project, fixture("MH 1", universe, 1)).id();
		head2 = repos.fixtures().create(project, fixture("MH 2", universe, 20)).id();
	}

	@Test
	void figuresAndMovesSurviveReopening() {
		var figure = repos.figures().create(project, new FigureSpec("Eight", true,
				List.of(FigurePoint.ofDmx(64, 128), FigurePoint.ofDmx(96.5, 192))));
		var spec = new MoveSpec("Eight above the stage", figure.id(),
				List.of(new MoveFixture(head2, 0.1, -0.05, true, false, true), MoveFixture.of(head1)), 0.8, 0.5,
				20 / 255.0, -10 / 255.0, 15, 6.5, MoveRepeat.ONCE, 2, OnceStart.TOGETHER);
		var move = repos.moves().create(project, spec);

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.figures().findById(figure.id()).orElseThrow()).isEqualTo(figure);
		assertThat(reopened.moves().findById(move.id()).orElseThrow().spec()).isEqualTo(spec);
		assertThat(reopened.moves().findByProject(project)).hasSize(1);

		var changed = repos.figures().update(figure.id(), new FigureSpec("Point", false,
				List.of(FigurePoint.ofDmx(1, 2))));
		assertThat(changed.spec().points()).hasSize(1);
		assertThat(changed.spec().closed()).isFalse();
	}

	@Test
	void aFixtureLeavingThePatchDropsOutOfItsMoves() {
		var figure = repos.figures().create(project, new FigureSpec("Spot", true, List.of(FigurePoint.ofDmx(1, 1))));
		var move = repos.moves().create(project, MoveSpec.of("Spot", figure.id(),
				List.of(MoveFixture.of(head1), MoveFixture.of(head2))));
		repos.fixtures().delete(head1);
		assertThat(repos.moves().findById(move.id()).orElseThrow().spec().fixtureIds()).containsExactly(head2);
	}

	@Test
	void aFigureInUseCannotBeDeleted() {
		var figure = repos.figures().create(project, new FigureSpec("Spot", true, List.of(FigurePoint.ofDmx(1, 1))));
		repos.moves().create(project, MoveSpec.of("Spot", figure.id(), List.of()));
		assertThatThrownBy(() -> repos.figures().delete(figure.id())).isInstanceOf(DataAccessException.class);
	}

	@Test
	void scenesKeysAndPartyFieldsPlayMoves() {
		var figure = repos.figures().create(project, new FigureSpec("Spot", true, List.of(FigurePoint.ofDmx(1, 1))));
		var a = repos.moves().create(project, MoveSpec.of("A", figure.id(), List.of(MoveFixture.of(head1)))).id();
		var b = repos.moves().create(project, MoveSpec.of("B", figure.id(), List.of(MoveFixture.of(head2)))).id();
		var key = SceneKey.move(2, b, KeyMode.FLASH, Duration.ofMillis(300));
		var scene = repos.scenes().create(project, new SceneSpec(SceneNumber.of(1), "Song", Optional.empty(),
				Optional.empty()), List.of(), List.of(key));
		repos.scenes().replaceSection(scene.id(), "", List.of(), List.of(), List.of(SceneMove.of(b), SceneMove.of(a)));
		repos.scenes().replaceSection(scene.id(), "Chorus", List.of(), List.of(), List.of(SceneMove.of(a)));

		var loaded = new SqliteRepositories(SqliteDatabase.open(dbFile)).scenes().findById(scene.id()).orElseThrow();
		assertThat(loaded.moves()).containsExactly(SceneMove.of(b), SceneMove.of(a));
		assertThat(loaded.section("Chorus").moves()).containsExactly(SceneMove.of(a));
		assertThat(loaded.keys()).containsExactly(key);
		assertThat(loaded.containsMove(b)).isTrue();

		repos.partyMatrices().save(project, new PartyMatrix(2, 2, Set.of(), List.of(PartyCell.move(1, 1, a))));
		assertThat(repos.partyMatrices().find(project).cell(1, 1).orElseThrow().moveId()).contains(a);
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-moving-head", "16ch", universe, address,
				Optional.empty(), 0);
	}
}
