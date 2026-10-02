package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.backup.port.out.DatabaseBackups;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backs up the database with {@code VACUUM INTO}, which writes a consistent copy while the application keeps
 * running. Only the newest {@code keep} backups are kept.
 *
 * <p>To restore a backup, stop Lumen and copy the file over the database file.
 */
public class SqliteBackups implements DatabaseBackups {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
			.withZone(ZoneOffset.UTC);
	private static final Pattern NAME = Pattern.compile("lumen-\\d{8}-\\d{6}-\\d{3}(-\\d+)?-[a-z0-9-]+\\.db");
	private static final Pattern REASON = Pattern.compile("[a-z0-9-]+");

	private final JdbcClient jdbc;
	private final Path directory;
	private final int keep;
	private final Clock clock;

	public SqliteBackups(DataSource dataSource, Path directory, int keep, Clock clock) {
		if (keep < 1) {
			throw new IllegalArgumentException("keep must be at least 1, was " + keep);
		}
		this.jdbc = JdbcClient.create(dataSource);
		this.directory = directory.toAbsolutePath().normalize();
		this.keep = keep;
		this.clock = clock;
	}

	@Override
	public synchronized Backup create(String reason) {
		if (!REASON.matcher(reason).matches()) {
			throw new IllegalArgumentException("Invalid backup reason: " + reason);
		}
		try {
			Files.createDirectories(directory);
			var stamp = TIMESTAMP.format(clock.instant());
			var file = directory.resolve("lumen-" + stamp + "-" + reason + ".db");
			for (int i = 2; Files.exists(file); i++) {
				file = directory.resolve("lumen-" + stamp + "-" + i + "-" + reason + ".db");
			}
			jdbc.sql("VACUUM INTO ?").param(file.toString()).update();
			prune();
			return toBackup(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot write backup to " + directory, e);
		}
	}

	@Override
	public List<Backup> list() {
		return files().map(this::toBackup).toList();
	}

	@Override
	public String location() {
		return directory.toString();
	}

	private void prune() throws IOException {
		for (var old : files().skip(keep).toList()) {
			Files.deleteIfExists(old);
		}
	}

	/** Backup files, newest first (the name starts with the time). */
	private Stream<Path> files() {
		if (!Files.isDirectory(directory)) {
			return Stream.empty();
		}
		try (var entries = Files.list(directory)) {
			return entries.filter(p -> NAME.matcher(p.getFileName().toString()).matches())
					.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
					.toList()
					.stream();
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot list backups in " + directory, e);
		}
	}

	private Backup toBackup(Path file) {
		try {
			return new Backup(file.getFileName().toString(), Files.getLastModifiedTime(file).toInstant(),
					Files.size(file));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
