package de.vinzens.lumen.application.settings.port.out;

import java.util.Optional;

/**
 * Application-wide settings that are not part of a project, stored as key/value pairs.
 */
public interface SettingsRepository {

	Optional<String> get(String key);

	void put(String key, String value);

	void remove(String key);
}
