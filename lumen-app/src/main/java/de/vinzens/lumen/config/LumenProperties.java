package de.vinzens.lumen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Technical settings of Lumen ({@code lumen.*} in application.yml).
 * Relative paths are resolved against the working directory.
 *
 * @param fixtureLibraryDir directory of the fixture library (YAML)
 * @param fixtureLibraryWatchInterval how often the library directory is checked for changed files
 * @param databaseFile      SQLite file containing the project data
 * @param backup            automatic database backups
 * @param networkAccess     accept connections from other devices regardless of the setting in the UI
 */
@ConfigurationProperties("lumen")
public record LumenProperties(
		@DefaultValue("fixture-types") String fixtureLibraryDir,
		@DefaultValue("2s") Duration fixtureLibraryWatchInterval,
		@DefaultValue("data/lumen.db") String databaseFile,
		@DefaultValue Output output,
		@DefaultValue ArtNet artnet,
		@DefaultValue Backup backup,
		@DefaultValue("false") boolean networkAccess) {

	// bound as String: Spring's String → Path conversion resolves through the servlet context in a running
	// web server and rejects relative paths like "../fixture-types"

	public Path fixtureLibraryPath() {
		return Path.of(fixtureLibraryDir);
	}

	public Path databasePath() {
		return Path.of(databaseFile);
	}

	/**
	 * @param dir  directory for backup files
	 * @param keep how many backups are kept; older ones are deleted
	 */
	public record Backup(@DefaultValue("data/backups") String dir, @DefaultValue("30") int keep) {

		public Path path() {
			return Path.of(dir);
		}
	}

	/**
	 * @param rateHz render loop rate; Art-Net allows at most 44 frames per second
	 */
	public record Output(@DefaultValue("40") int rateHz) {
	}

	/**
	 * @param enabled          send Art-Net packets and discover nodes at all (disabled in tests)
	 * @param bindAddress      local address to send from and listen on
	 * @param port             destination UDP port of nodes
	 * @param broadcastAddress destination for broadcast universes and ArtPoll
	 * @param listenPort       local port for ArtPollReply (nodes answer to 6454)
	 * @param pollInterval     how often nodes are discovered via ArtPoll
	 */
	public record ArtNet(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("0.0.0.0") String bindAddress,
			@DefaultValue("6454") int port,
			@DefaultValue("255.255.255.255") String broadcastAddress,
			@DefaultValue("6454") int listenPort,
			@DefaultValue("3s") Duration pollInterval) {
	}
}
