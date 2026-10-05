package de.vinzens.lumen.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
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

	/** Remembers which version of every shipped profile was installed (path → SHA-256). */
	static final String MANIFEST = ".bundled-profiles.properties";
	/** Suffix of a newer shipped profile put next to one the user changed; the library does not load it. */
	static final String SHIPPED_SUFFIX = ".shipped";

	/**
	 * What {@link #prepare} did with the shipped fixture profiles.
	 *
	 * @param copied  new profiles
	 * @param updated profiles replaced by a newer shipped version, because the user had not changed them
	 * @param kept    profiles the user changed: kept, the newer shipped version lies next to them as {@code .shipped}
	 */
	public record Profiles(List<Path> copied, List<Path> updated, List<Path> kept) {
	}

	/**
	 * Creates the directories and brings the fixture profiles shipped with the app into the library. New profiles are
	 * copied, profiles the user has not changed are updated to the shipped version, profiles the user changed or
	 * deleted are left alone.
	 *
	 * @param bundledLibrary fixture profiles inside the app bundle, may be missing
	 */
	public Profiles prepare(Path bundledLibrary) {
		try {
			Files.createDirectories(dataDir);
			Files.createDirectories(logDir);
			Files.createDirectories(fixtureLibrary());
			return Files.isDirectory(bundledLibrary)
					? install(bundledLibrary, fixtureLibrary())
					: new Profiles(List.of(), List.of(), List.of());
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot prepare " + dataDir, e);
		}
	}

	static Profiles install(Path from, Path to) throws IOException {
		var manifestFile = to.resolve(MANIFEST);
		var manifest = new Properties();
		if (Files.exists(manifestFile)) {
			try (var in = Files.newBufferedReader(manifestFile)) {
				manifest.load(in);
			}
		}
		var copied = new ArrayList<Path>();
		var updated = new ArrayList<Path>();
		var kept = new ArrayList<Path>();
		try (Stream<Path> files = Files.walk(from)) {
			for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
				var relative = from.relativize(file);
				var key = relative.toString().replace('\\', '/');
				var target = to.resolve(relative.toString());
				var shipped = sha256(file);
				var installed = manifest.getProperty(key);
				if (!Files.exists(target)) {
					if (installed != null) {
						continue; // installed before and deleted by the user
					}
					Files.createDirectories(target.getParent());
					Files.copy(file, target);
					copied.add(relative);
				} else {
					var current = sha256(target);
					if (current.equals(shipped)) {
						Files.deleteIfExists(shippedCopy(target));
					} else if (current.equals(installed)) {
						Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
						updated.add(relative);
					} else {
						// changed by the user (or installed before versions were remembered): keep it
						Files.copy(file, shippedCopy(target), StandardCopyOption.REPLACE_EXISTING);
						kept.add(relative);
						continue;
					}
				}
				manifest.setProperty(key, shipped);
			}
		}
		try (var out = Files.newBufferedWriter(manifestFile)) {
			manifest.store(out, "Fixture profiles shipped with Lumen, as installed – do not edit");
		}
		return new Profiles(copied, updated, kept);
	}

	private static Path shippedCopy(Path target) {
		return target.resolveSibling(target.getFileName() + SHIPPED_SUFFIX);
	}

	private static String sha256(Path file) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
