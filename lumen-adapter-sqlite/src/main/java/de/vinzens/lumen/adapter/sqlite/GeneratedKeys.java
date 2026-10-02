package de.vinzens.lumen.adapter.sqlite;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Objects;

final class GeneratedKeys {

	private GeneratedKeys() {
	}

	/** SQLite returns generated keys as Integer or Long depending on their size. */
	static long generatedId(KeyHolder keys) {
		return Objects.requireNonNull(keys.getKey(), "No generated key").longValue();
	}
}
