package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.library.yaml.LibraryDirectoryWatcher;
import de.vinzens.lumen.adapter.library.yaml.YamlFixtureTypeSource;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.library.FixtureLibraryReloader;
import de.vinzens.lumen.application.library.FixtureLibraryService;
import de.vinzens.lumen.application.library.port.out.FixtureTypeSource;
import de.vinzens.lumen.application.output.OutputEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FixtureLibraryConfiguration {

	private static final Logger log = LoggerFactory.getLogger(FixtureLibraryConfiguration.class);

	@Bean
	FixtureTypeSource fixtureTypeSource(LumenProperties properties) {
		return new YamlFixtureTypeSource(properties.fixtureLibraryPath());
	}

	@Bean
	FixtureLibraryService fixtureLibraryService(FixtureTypeSource source, LumenProperties properties) {
		var service = new FixtureLibraryService(source);
		service.reload();
		log.info("Fixture library loaded from '{}': {} fixture types",
				properties.fixtureLibraryPath().toAbsolutePath().normalize(), service.fixtureTypes().size());
		service.issues().forEach(issue -> {
			var message = "{}{}: {}";
			var typeId = issue.fixtureTypeId().map(id -> " [" + id + "]").orElse("");
			if (issue.isError()) {
				log.error(message, issue.source(), typeId, issue.message());
			} else {
				log.warn(message, issue.source(), typeId, issue.message());
			}
		});
		return service;
	}

	@Bean
	FixtureLibraryReloader fixtureLibraryReloader(FixtureLibraryService library, OutputEngine outputEngine,
			StateEvents stateEvents) {
		return new FixtureLibraryReloader(library, outputEngine, stateEvents);
	}

	/** Reloads the library a moment after a YAML file was added, changed or removed. */
	@Bean(destroyMethod = "close")
	LibraryDirectoryWatcher libraryDirectoryWatcher(LumenProperties properties, FixtureLibraryReloader reloader) {
		var watcher = new LibraryDirectoryWatcher(properties.fixtureLibraryPath(),
				properties.fixtureLibraryWatchInterval(), reloader::reload);
		watcher.start();
		return watcher;
	}
}
