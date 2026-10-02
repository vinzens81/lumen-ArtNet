package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.library.yaml.YamlFixtureTypeSource;
import de.vinzens.lumen.application.library.FixtureLibraryService;
import de.vinzens.lumen.application.library.port.out.FixtureTypeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FixtureLibraryConfiguration {

	private static final Logger log = LoggerFactory.getLogger(FixtureLibraryConfiguration.class);

	@Bean
	FixtureTypeSource fixtureTypeSource(LumenProperties properties) {
		return new YamlFixtureTypeSource(properties.fixtureLibraryDir());
	}

	@Bean
	FixtureLibraryService fixtureLibraryService(FixtureTypeSource source, LumenProperties properties) {
		var service = new FixtureLibraryService(source);
		service.reload();
		log.info("Fixture library loaded from '{}': {} fixture types",
				properties.fixtureLibraryDir().toAbsolutePath().normalize(), service.fixtureTypes().size());
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
}
