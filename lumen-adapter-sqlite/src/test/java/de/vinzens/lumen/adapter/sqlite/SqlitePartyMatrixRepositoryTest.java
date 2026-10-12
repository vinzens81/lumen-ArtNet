package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.party.PartyPage;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.FixtureSpec;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlitePartyMatrixRepositoryTest {

	@TempDir
	Path dir;

	private Path dbFile;
	private SqliteRepositories repos;
	private long project;
	private long par1;
	private long par2;

	@BeforeEach
	void setUp() {
		dbFile = dir.resolve("lumen.db");
		repos = new SqliteRepositories(SqliteDatabase.open(dbFile));
		project = repos.projects().create(new ProjectSpec("Show", Optional.empty(), Duration.ZERO, Duration.ZERO),
				Instant.EPOCH).id();
		var universe = repos.universes().create(project, new UniverseSpec(1, Optional.empty(),
				new ArtNetAddress(0, 0, 0), new ArtNetTarget.Broadcast())).id();
		par1 = repos.fixtures().create(project, fixture("PAR 1", universe, 1)).id();
		par2 = repos.fixtures().create(project, fixture("PAR 2", universe, 10)).id();
	}

	@Test
	void gradientsOverSeveralColumnsSurviveReopening() {
		var matrix = new PartyMatrix(2, 8, Set.of(1), List.of(PartyCell.text(0, 0, "PAR"),
				PartyCell.fader(1, 1, 3, new PartyFader(PartyFader.Kind.DIMMER, List.of(par2, par1)))
						.inGroup("PAR – intensity"),
				PartyCell.fader(1, 4, 4, new PartyFader(PartyFader.Kind.COLOR, List.of(par1)))));
		repos.partyMatrices().save(project, matrix);

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.partyMatrices().find(project)).isEqualTo(matrix);

		repos.partyMatrices().save(project, new PartyMatrix(2, 8, Set.of(), List.of()));
		assertThat(repos.partyMatrices().find(project).pages().getFirst().cells()).isEmpty();
	}

	@Test
	void pagesWithPadsOverSeveralRowsSurviveReopening() {
		var matrix = PartyMatrix.of(new PartyPage("Colors", 1, 2, Set.of(), List.of(PartyCell.text(0, 1, "Red"))),
				new PartyPage("Movers", 3, 4, Set.of(2), List.of(
						PartyCell.fader(0, 0, 2, 2, new PartyFader(PartyFader.Kind.PAN_TILT, List.of(par1, par2))),
						PartyCell.text(2, 0, "Below"))));
		repos.partyMatrices().save(project, matrix);

		var reopened = new SqliteRepositories(SqliteDatabase.open(dbFile));
		assertThat(reopened.partyMatrices().find(project)).isEqualTo(matrix);

		repos.partyMatrices().save(project, PartyMatrix.EMPTY);
		assertThat(repos.partyMatrices().find(project)).isEqualTo(PartyMatrix.EMPTY);
	}

	@Test
	void deletedFixturesDropOutOfAGradientAndTheLastOneLeavesAText() {
		repos.partyMatrices().save(project, new PartyMatrix(1, 4, Set.of(), List.of(
				PartyCell.fader(0, 0, 2, new PartyFader(PartyFader.Kind.DIMMER, List.of(par1, par2))),
				PartyCell.fader(0, 2, 2, new PartyFader(PartyFader.Kind.COLOR, List.of(par1))))));

		repos.fixtures().delete(par1);

		var matrix = repos.partyMatrices().find(project).pages().getFirst();
		assertThat(matrix.cell(0, 0).orElseThrow().fader().orElseThrow().fixtureIds()).containsExactly(par2);
		var empty = matrix.cell(0, 2).orElseThrow();
		assertThat(empty.fader()).isEmpty();
		assertThat(empty.span()).isEqualTo(2);
	}

	@Test
	void theMatrixBeforePagesBecomesTheFirstPage() {
		var file = dir.resolve("before-pages.db");
		var config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		var dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("28").load().migrate();
		var jdbc = JdbcClient.create(dataSource);
		jdbc.sql("INSERT INTO project (id, name, active, created_at, updated_at) VALUES (1, 'Show', 1, '"
				+ Instant.EPOCH + "', '" + Instant.EPOCH + "')").update();
		jdbc.sql("INSERT INTO party_matrix (project_id, rows, columns, separators) VALUES (1, 3, 5, '2')").update();
		jdbc.sql("INSERT INTO party_cell (project_id, row_index, column_index, label, color, border_color, key_mode, "
				+ "span) VALUES (1, 1, 1, 'Colors', '#000000', '#ffffff', 'toggle', 3)").update();

		var matrix = new SqliteRepositories(SqliteDatabase.open(file)).partyMatrices().find(1);

		assertThat(matrix).isEqualTo(PartyMatrix.of(new PartyPage("Party", 3, 5, Set.of(2), List.of(
				PartyCell.text(1, 1, "Colors").withColors("#000000", "#ffffff").withSize(3, 1)))));
	}

	private static FixtureSpec fixture(String name, long universe, int address) {
		return new FixtureSpec(name, Optional.empty(), "generic-rgbw-par", "4ch", universe, address,
				Optional.empty(), 0);
	}
}
