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
		@DefaultValue("fixture-types") String fixtureLibraryDir,
		@DefaultValue("data/lumen.db") String databaseFile,
		@DefaultValue Output output,
		@DefaultValue ArtNet artnet) {

	// bound as String: Spring's String → Path conversion resolves through the servlet context in a running
	// web server and rejects relative paths like "../fixture-types"

	public Path fixtureLibraryPath() {
		return Path.of(fixtureLibraryDir);
	}

	public Path databasePath() {
		return Path.of(databaseFile);
	}

	/**
	 * @param rateHz render loop rate; Art-Net allows at most 44 frames per second
	 */
	public record Output(@DefaultValue("40") int rateHz) {
	}

	/**
	 * @param enabled          send Art-Net packets at all (disabled in tests)
	 * @param bindAddress      local address to send from
	 * @param port             destination UDP port
	 * @param broadcastAddress destination for universes with target {@code broadcast}
	 */
	public record ArtNet(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("0.0.0.0") String bindAddress,
			@DefaultValue("6454") int port,
			@DefaultValue("255.255.255.255") String broadcastAddress) {
	}
}
