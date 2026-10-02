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

	public SqliteRepositories(DataSource dataSource) {
		var jdbc = JdbcClient.create(dataSource);
		var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		this.projects = new SqliteProjectRepository(jdbc, transactions);
		this.universes = new SqliteUniverseRepository(jdbc);
		this.fixtures = new SqliteFixtureRepository(jdbc);
		this.groups = new SqliteGroupRepository(jdbc, transactions);
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
}
