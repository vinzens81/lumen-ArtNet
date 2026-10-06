package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.sqlite.SqliteBackups;
import de.vinzens.lumen.adapter.sqlite.SqliteDatabase;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.backup.BackupService;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.composition.CompositionService;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.legacy.LegacyMigrationService;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.LookService;
import de.vinzens.lumen.application.output.OperatingModeKeeper;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.project.PatchService;
import de.vinzens.lumen.application.project.ProjectService;
import de.vinzens.lumen.application.project.ProjectTransferService;
import de.vinzens.lumen.application.project.StageService;
import de.vinzens.lumen.application.party.PartyMatrixService;
import de.vinzens.lumen.application.playback.FaderService;
import de.vinzens.lumen.application.preset.PresetService;
import de.vinzens.lumen.application.scene.SceneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
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
	ProjectTransferService projectTransferService(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			Clock clock, OutputEngine outputEngine) {
		return new ProjectTransferService(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), repositories.looks(), repositories.compositions(), repositories.scenes(),
				repositories.stagePlans(), repositories.partyMatrices(), repositories.presets(), repositories.faders(),
				library, clock, outputEngine);
	}

	/**
	 * Converts shows from before looks (iteration S) once, before anything plays them. A backup of the database is
	 * made first; the old data also stays in the database.
	 */
	@Bean
	LegacyMigrationService legacyMigration(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			SqliteBackups backups, Clock clock) {
		var migration = new LegacyMigrationService(repositories.legacyShows(), repositories.projects(),
				repositories.fixtures(), repositories.groups(), repositories.looks(), repositories.compositions(),
				repositories.scenes(), repositories.faders(), library, clock);
		if (!repositories.legacyShows().pendingProjects().isEmpty()) {
			var backup = backups.create("before-looks");
			log.info("Converting shows into looks, compositions and scenes; backup: {}", backup.name());
			migration.convertPending();
		}
		return migration;
	}

	/** Created after old shows are converted, so nothing plays them half converted. */
	@Bean
	CompositionResolver compositionResolver(SqliteRepositories repositories, LegacyMigrationService legacyMigration) {
		return new CompositionResolver(repositories.compositions(), repositories.looks(), repositories.scenes());
	}

	@Bean
	LookService lookService(SqliteRepositories repositories, OutputEngine outputEngine, StateEvents stateEvents,
			Clock clock) {
		return new LookService(repositories.projects(), repositories.looks(), repositories.compositions(),
				repositories.scenes(),
				outputEngine, stateEvents, clock);
	}

	@Bean
	CompositionService compositionService(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			OutputEngine outputEngine, StateEvents stateEvents) {
		return new CompositionService(repositories.projects(), repositories.compositions(), repositories.looks(),
				repositories.scenes(), repositories.fixtures(), repositories.groups(), library, outputEngine,
				stateEvents);
	}

	@Bean
	StageService stageService(SqliteRepositories repositories, OutputEngine outputEngine, StateEvents stateEvents) {
		return new StageService(repositories.projects(), repositories.fixtures(), repositories.stagePlans(),
				outputEngine, stateEvents);
	}

	@Bean
	SqliteBackups databaseBackups(SqliteRepositories repositories, LumenProperties properties, Clock clock) {
		// file names in local time, easier to find when restoring by hand
		var backups = new SqliteBackups(repositories.dataSource(), properties.backup().path(),
				properties.backup().keep(), clock.withZone(ZoneId.systemDefault()));
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

	/** Created after the saved operating mode is restored, so restoring show mode does not count as entering it. */
	@Bean
	BackupService backupService(SqliteBackups backups, OutputEngine outputEngine, SqliteRepositories repositories,
			ExecutorService backupExecutor, StateEvents stateEvents, OperatingModeKeeper operatingModeKeeper) {
		var service = new BackupService(backups, outputEngine, repositories.projects(), backupExecutor, stateEvents);
		service.backupInBackground("startup");
		return service;
	}

	@Bean
	SceneService sceneService(SqliteRepositories repositories, LookService lookService,
			CompositionResolver compositionResolver, OutputEngine outputEngine, StateEvents stateEvents) {
		return new SceneService(repositories.projects(), repositories.scenes(), repositories.compositions(),
				repositories.looks(), lookService, compositionResolver, outputEngine, stateEvents);
	}

	@Bean
	FaderService faderService(SqliteRepositories repositories, StateEvents stateEvents) {
		return new FaderService(repositories.projects(), repositories.compositions(), repositories.faders(), stateEvents);
	}

	@Bean
	PresetService presetService(SqliteRepositories repositories, OutputEngine outputEngine, StateEvents stateEvents) {
		return new PresetService(repositories.projects(), repositories.presets(), repositories.looks(), outputEngine,
				stateEvents);
	}

	@Bean
	PartyMatrixService partyMatrixService(SqliteRepositories repositories, StateEvents stateEvents) {
		return new PartyMatrixService(repositories.projects(), repositories.scenes(), repositories.compositions(),
				repositories.partyMatrices(), stateEvents);
	}
}
