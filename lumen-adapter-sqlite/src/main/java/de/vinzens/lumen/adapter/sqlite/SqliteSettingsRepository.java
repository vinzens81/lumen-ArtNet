package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.settings.port.out.SettingsRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class SqliteSettingsRepository implements SettingsRepository {

	private final JdbcClient jdbc;

	public SqliteSettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<String> get(String key) {
		return jdbc.sql("SELECT value FROM app_setting WHERE key = ?").param(key).query(String.class).optional();
	}

	@Override
	public void put(String key, String value) {
		jdbc.sql("""
				INSERT INTO app_setting (key, value) VALUES (?, ?)
				ON CONFLICT (key) DO UPDATE SET value = excluded.value""")
				.params(key, value)
				.update();
	}

	@Override
	public void remove(String key) {
		jdbc.sql("DELETE FROM app_setting WHERE key = ?").param(key).update();
	}
}
