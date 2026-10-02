package de.vinzens.lumen.adapter.sqlite;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Objects;

final class GeneratedKeys {

	private GeneratedKeys() {
	}

	/** SQLite liefert generierte Schlüssel je nach Größe als Integer oder Long. */
	static long generatedId(KeyHolder keys) {
		return Objects.requireNonNull(keys.getKey(), "Kein generierter Schlüssel").longValue();
	}
}
