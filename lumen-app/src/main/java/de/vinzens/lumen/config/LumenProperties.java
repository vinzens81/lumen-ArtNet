package de.vinzens.lumen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Technische Einstellungen von Lumen ({@code lumen.*} in der application.yml).
 * Relative Pfade beziehen sich auf das Arbeitsverzeichnis.
 *
 * @param fixtureLibraryDir Ordner der Gerätebibliothek (YAML)
 * @param databaseFile      SQLite-Datei mit den Projektdaten
 */
@ConfigurationProperties("lumen")
public record LumenProperties(
		@DefaultValue("fixture-types") Path fixtureLibraryDir,
		@DefaultValue("data/lumen.db") Path databaseFile) {
}
