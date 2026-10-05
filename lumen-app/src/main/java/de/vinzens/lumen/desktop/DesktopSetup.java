package de.vinzens.lumen.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the Mac app keeps its data. The app bundle is read-only, so database, backups and the fixture library live in
 * {@code ~/Library/Application Support/Lumen}, the log in {@code ~/Library/Logs/Lumen}.
 *
 * @param dataDir database, backups and the fixture library
 * @param logDir  log files
 */
public record DesktopSetup(Path dataDir, Path logDir) {

	public static DesktopSetup forUser(Path home) {
		return new DesktopSetup(home.resolve("Library/Application Support/Lumen"), home.resolve("Library/Logs/Lumen"));
	}

	public Path fixtureLibrary() {
		return dataDir.resolve("fixture-types");
	}

	public Path logFile() {
		return logDir.resolve("lumen.log");
	}

	/** Settings that point Lumen to these directories, given as command line arguments so they win. */
	public List<String> arguments() {
		return List.of(
				"--lumen.database-file=" + dataDir.resolve("lumen.db"),
				"--lumen.backup.dir=" + dataDir.resolve("backups"),
				"--lumen.fixture-library-dir=" + fixtureLibrary(),
				"--logging.file.name=" + logFile());
	}

	/**
	 * Creates the directories and copies the fixture profiles shipped with the app into the library – only files that
	 * are not there yet, so profiles the user changed or added are kept.
	 *
	 * @param bundledLibrary fixture profiles inside the app bundle, may be missing
	 * @return the copied files, relative to the library
	 */
	public List<Path> prepare(Path bundledLibrary) {
		try {
			Files.createDirectories(dataDir);
			Files.createDirectories(logDir);
			Files.createDirectories(fixtureLibrary());
			return Files.isDirectory(bundledLibrary) ? copyMissing(bundledLibrary, fixtureLibrary()) : List.of();
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot prepare " + dataDir, e);
		}
	}

	static List<Path> copyMissing(Path from, Path to) throws IOException {
		var copied = new ArrayList<Path>();
		try (Stream<Path> files = Files.walk(from)) {
			for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
				var relative = from.relativize(file);
				var target = to.resolve(relative.toString());
				if (!Files.exists(target)) {
					Files.createDirectories(target.getParent());
					Files.copy(file, target);
					copied.add(relative);
				}
			}
		}
		return copied;
	}
}
