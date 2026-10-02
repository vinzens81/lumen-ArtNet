package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.access.AccessService;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.output.OutputEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.net.InetAddress;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AccessConfiguration.class);

	@Bean
	AccessService accessService(SqliteRepositories repositories, OutputEngine outputEngine, Clock clock,
			StateEvents stateEvents, LumenProperties properties) {
		var access = new AccessService(repositories.settings(), outputEngine, clock, stateEvents,
				properties.networkAccess());
		log.info("Configuration PIN: {}", access.pinSet() ? "set" : "not set");
		return access;
	}

	/**
	 * Listens on all interfaces when network access is enabled; otherwise {@code server.address} (localhost)
	 * applies. Runs after Spring Boot's own customizer, which sets {@code server.address}.
	 */
	@Bean
	NetworkAccessCustomizer networkAccessCustomizer(AccessService access) {
		return new NetworkAccessCustomizer(access);
	}

	record NetworkAccessCustomizer(AccessService access)
			implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>, Ordered {

		@Override
		public void customize(ConfigurableWebServerFactory factory) {
			if (access.networkAccessActive()) {
				factory.setAddress(InetAddress.ofLiteral("0.0.0.0"));
				log.warn("Network access is enabled: other devices can connect to Lumen");
			}
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}
	}
}
