package de.vinzens.lumen.adapter.library.yaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryDirectoryWatcherTest {

	@TempDir
	Path dir;

	private final AtomicInteger changes = new AtomicInteger();

	@Test
	void reportsAChangeOnceTheFilesAreStable() throws IOException {
		Files.writeString(dir.resolve("par.yml"), "id: par");
		Files.writeString(dir.resolve("notes.txt"), "ignored");
		try (var watcher = new LibraryDirectoryWatcher(dir, Duration.ofHours(1), changes::incrementAndGet)) {
			watcher.start();
			watcher.check();
			assertThat(changes).hasValue(0);

			Files.createDirectories(dir.resolve("diy"));
			Files.writeString(dir.resolve("diy/dimmer.yaml"), "id: dimmer");
			watcher.check();
			assertThat(changes).as("still changing?").hasValue(0);
			watcher.check();
			assertThat(changes).hasValue(1);
			watcher.check();
			assertThat(changes).hasValue(1);

			Files.writeString(dir.resolve("notes.txt"), "other files do not count");
			watcher.check();
			watcher.check();
			assertThat(changes).hasValue(1);

			Files.delete(dir.resolve("par.yml"));
			watcher.check();
			watcher.check();
			assertThat(changes).hasValue(2);
		}
	}
}
