package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.project.ProjectSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteBackupsTest {

	private static final Instant NOW = Instant.parse("2026-10-02T19:30:05Z");

	@TempDir
	Path dir;

	@Test
	void backupIsACompleteDatabaseThatCanBeOpened() {
		var repos = new SqliteRepositories(SqliteDatabase.open(dir.resolve("lumen.db")));
		repos.projects().create(new ProjectSpec("Tempest", Optional.empty(), Duration.ZERO, Duration.ZERO), NOW);
		var backups = new SqliteBackups(repos.dataSource(), dir.resolve("backups"), 5, Clock.fixed(NOW, ZoneOffset.UTC));

		var backup = backups.create("show");

		assertThat(backup.name()).isEqualTo("lumen-20261002-193005-000-show.db");
		assertThat(backup.sizeBytes()).isPositive();
		var restored = new SqliteRepositories(SqliteDatabase.open(dir.resolve("backups").resolve(backup.name())));
		assertThat(restored.projects().findAll()).extracting(p -> p.spec().name()).containsExactly("Tempest");
	}

	@Test
	void keepsOnlyTheNewestBackups() {
		var repos = new SqliteRepositories(SqliteDatabase.open(dir.resolve("lumen.db")));
		var clock = new Clock[] {Clock.fixed(NOW, ZoneOffset.UTC)};
		var backups = new SqliteBackups(repos.dataSource(), dir.resolve("backups"), 2, new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return clock[0].instant();
			}
		});

		backups.create("startup");
		clock[0] = Clock.offset(clock[0], Duration.ofMillis(1));
		backups.create("show");
		clock[0] = Clock.offset(clock[0], Duration.ofMinutes(1));
		backups.create("project");

		assertThat(backups.list()).extracting(b -> b.name())
				.containsExactly("lumen-20261002-193105-001-project.db", "lumen-20261002-193005-001-show.db");
		assertThatThrownBy(() -> backups.create("../evil")).isInstanceOf(IllegalArgumentException.class);
	}
}
