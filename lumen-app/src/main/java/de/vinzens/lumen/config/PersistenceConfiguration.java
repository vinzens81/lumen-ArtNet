package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.sqlite.SqliteBackups;
import de.vinzens.lumen.adapter.sqlite.SqliteDatabase;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.backup.BackupService;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.project.PatchService;
import de.vinzens.lumen.application.project.ProjectService;
import de.vinzens.lumen.application.project.ProjectTransferService;
import de.vinzens.lumen.application.scene.SceneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
class PersistenceConfiguration {

	private static final Logger log = LoggerFactory.getLogger(PersistenceConfiguration.class);

	@Bean
	SqliteRepositories sqliteRepositories(LumenProperties properties) {
		var file = properties.databasePath().toAbsolutePath().normalize();
		log.info("Project database: {}", file);
		return new SqliteRepositories(SqliteDatabase.open(file));
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ProjectService projectService(SqliteRepositories repositories, Clock clock, OutputEngine outputEngine) {
		return new ProjectService(repositories.projects(), clock, outputEngine);
	}

	@Bean
	PatchService patchService(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			OutputEngine outputEngine) {
		return new PatchService(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), library, outputEngine);
	}

	@Bean
	ProjectTransferService projectTransferService(SqliteRepositories repositories, Clock clock,
			OutputEngine outputEngine) {
		return new ProjectTransferService(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), repositories.scenes(), clock, outputEngine);
	}

	@Bean
	SqliteBackups databaseBackups(SqliteRepositories repositories, LumenProperties properties, Clock clock) {
		var backups = new SqliteBackups(repositories.dataSource(), properties.backup().path(),
				properties.backup().keep(), clock);
		log.info("Database backups: {} (keeping {})", backups.location(), properties.backup().keep());
		return backups;
	}

	/** Runs automatic backups in the background; a running backup is finished on shutdown. */
	@Bean(destroyMethod = "close")
	ExecutorService backupExecutor() {
		return Executors.newSingleThreadExecutor(runnable -> {
			var thread = new Thread(runnable, "lumen-backup");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Bean
	BackupService backupService(SqliteBackups backups, OutputEngine outputEngine, SqliteRepositories repositories,
			ExecutorService backupExecutor, StateEvents stateEvents) {
		var service = new BackupService(backups, outputEngine, repositories.projects(), backupExecutor, stateEvents);
		service.backupInBackground("startup");
		return service;
	}

	@Bean
	SceneService sceneService(SqliteRepositories repositories, OutputEngine outputEngine, StateEvents stateEvents) {
		return new SceneService(repositories.projects(), repositories.scenes(), outputEngine, stateEvents);
	}
}
