package de.vinzens.lumen.adapter.sqlite;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Creates all repositories for an opened database.
 */
public final class SqliteRepositories {

	private final SqliteProjectRepository projects;
	private final SqliteUniverseRepository universes;
	private final SqliteFixtureRepository fixtures;
	private final SqliteGroupRepository groups;
	private final SqliteSceneRepository scenes;
	private final SqliteSettingsRepository settings;
	private final SqlitePlaybackStateRepository playbackStates;
	private final SqliteStagePlanRepository stagePlans;
	private final SqliteCompositionRepository compositions;
	private final SqlitePartyButtonRepository partyButtons;

	private final DataSource dataSource;

	public SqliteRepositories(DataSource dataSource) {
		this.dataSource = dataSource;
		var jdbc = JdbcClient.create(dataSource);
		var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		this.projects = new SqliteProjectRepository(jdbc, transactions);
		this.universes = new SqliteUniverseRepository(jdbc);
		this.fixtures = new SqliteFixtureRepository(jdbc);
		this.groups = new SqliteGroupRepository(jdbc, transactions);
		this.scenes = new SqliteSceneRepository(jdbc, transactions);
		this.settings = new SqliteSettingsRepository(jdbc);
		this.playbackStates = new SqlitePlaybackStateRepository(jdbc);
		this.stagePlans = new SqliteStagePlanRepository(jdbc);
		this.compositions = new SqliteCompositionRepository(jdbc, transactions);
		this.partyButtons = new SqlitePartyButtonRepository(jdbc, transactions);
	}

	public DataSource dataSource() {
		return dataSource;
	}

	public SqliteProjectRepository projects() {
		return projects;
	}

	public SqliteUniverseRepository universes() {
		return universes;
	}

	public SqliteFixtureRepository fixtures() {
		return fixtures;
	}

	public SqliteGroupRepository groups() {
		return groups;
	}

	public SqliteSceneRepository scenes() {
		return scenes;
	}

	public SqliteSettingsRepository settings() {
		return settings;
	}

	public SqlitePlaybackStateRepository playbackStates() {
		return playbackStates;
	}

	public SqliteStagePlanRepository stagePlans() {
		return stagePlans;
	}

	public SqliteCompositionRepository compositions() {
		return compositions;
	}

	public SqlitePartyButtonRepository partyButtons() {
		return partyButtons;
	}
}
