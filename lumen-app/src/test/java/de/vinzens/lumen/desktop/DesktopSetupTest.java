package de.vinzens.lumen.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopSetupTest {

	@TempDir
	Path home;

	@Test
	void keepsDataInTheUsersLibrary() {
		var setup = DesktopSetup.forUser(home);

		assertThat(setup.arguments()).containsExactly(
				"--lumen.database-file=" + home.resolve("Library/Application Support/Lumen/lumen.db"),
				"--lumen.backup.dir=" + home.resolve("Library/Application Support/Lumen/backups"),
				"--lumen.fixture-library-dir=" + home.resolve("Library/Application Support/Lumen/fixture-types"),
				"--logging.file.name=" + home.resolve("Library/Logs/Lumen/lumen.log"));
	}

	@Test
	void copiesShippedProfilesWithoutOverwritingTheUsersOnes() throws IOException {
		var bundled = home.resolve("Lumen.app/Contents/app/fixture-types");
		write(bundled.resolve("generic/dimmer.yaml"), "shipped dimmer");
		write(bundled.resolve("generic/par.yaml"), "shipped par");
		var setup = DesktopSetup.forUser(home);
		write(setup.fixtureLibrary().resolve("generic/par.yaml"), "my own par");

		var copied = setup.prepare(bundled);

		assertThat(copied).containsExactly(Path.of("generic/dimmer.yaml"));
		assertThat(setup.fixtureLibrary().resolve("generic/dimmer.yaml")).hasContent("shipped dimmer");
		assertThat(setup.fixtureLibrary().resolve("generic/par.yaml")).hasContent("my own par");
		assertThat(setup.logDir()).isDirectory();
		assertThat(setup.prepare(bundled)).as("second start").isEmpty();
	}

	@Test
	void worksWithoutShippedProfiles() {
		var setup = DesktopSetup.forUser(home);

		assertThat(setup.prepare(home.resolve("missing"))).isEmpty();
		assertThat(setup.fixtureLibrary()).isDirectory();
	}

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
