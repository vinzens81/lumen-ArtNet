package de.vinzens.lumen.adapter.sqlite;

import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Öffnet die SQLite-Datenbankdatei und bringt das Schema per Flyway auf den neuesten Stand.
 */
public final class SqliteDatabase {

	private static final int BUSY_TIMEOUT_MS = 5_000;

	private SqliteDatabase() {
	}

	public static DataSource open(Path file) {
		createParentDirectories(file);

		var config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		config.setJournalMode(SQLiteConfig.JournalMode.WAL);
		config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
		config.setBusyTimeout(BUSY_TIMEOUT_MS);

		var dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file.toAbsolutePath());

		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.load()
				.migrate();
		return dataSource;
	}

	private static void createParentDirectories(Path file) {
		var parent = file.toAbsolutePath().getParent();
		try {
			Files.createDirectories(parent);
		} catch (IOException e) {
			throw new UncheckedIOException("Ordner für die Datenbank kann nicht angelegt werden: " + parent, e);
		}
	}
}
