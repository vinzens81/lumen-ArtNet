package de.vinzens.lumen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Technical settings of Lumen ({@code lumen.*} in application.yml).
 * Relative paths are resolved against the working directory.
 *
 * @param fixtureLibraryDir directory of the fixture library (YAML)
 * @param databaseFile      SQLite file containing the project data
 */
@ConfigurationProperties("lumen")
public record LumenProperties(
		@DefaultValue("fixture-types") Path fixtureLibraryDir,
		@DefaultValue("data/lumen.db") Path databaseFile) {
}
