package de.vinzens.lumen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Technische Einstellungen von Lumen ({@code lumen.*} in der application.yml).
 *
 * @param fixtureLibraryDir Ordner der Gerätebibliothek (YAML), relativ zum Arbeitsverzeichnis
 */
@ConfigurationProperties("lumen")
public record LumenProperties(@DefaultValue("fixture-types") Path fixtureLibraryDir) {
}
