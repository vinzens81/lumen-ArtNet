package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.sqlite.SqliteDatabase;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.project.PatchService;
import de.vinzens.lumen.application.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class PersistenceConfiguration {

	private static final Logger log = LoggerFactory.getLogger(PersistenceConfiguration.class);

	@Bean
	SqliteRepositories sqliteRepositories(LumenProperties properties) {
		var file = properties.databaseFile().toAbsolutePath().normalize();
		log.info("Projektdatenbank: {}", file);
		return new SqliteRepositories(SqliteDatabase.open(file));
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ProjectService projectService(SqliteRepositories repositories, Clock clock) {
		return new ProjectService(repositories.projects(), clock);
	}

	@Bean
	PatchService patchService(SqliteRepositories repositories, QueryFixtureLibraryUseCase library) {
		return new PatchService(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), library);
	}
}
