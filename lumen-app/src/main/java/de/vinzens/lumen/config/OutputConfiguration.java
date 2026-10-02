package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.artnet.ArtNetDiscovery;
import de.vinzens.lumen.adapter.artnet.ArtNetDmxOutput;
import de.vinzens.lumen.adapter.artnet.ArtNetSettings;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.ArtNetNodeService;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.output.RenderLoop;
import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class OutputConfiguration {

	private static final Logger log = LoggerFactory.getLogger(OutputConfiguration.class);

	@Bean
	ArtNetSettings artNetSettings(LumenProperties properties) {
		var artNet = properties.artnet();
		return new ArtNetSettings(artNet.bindAddress(), artNet.port(), artNet.broadcastAddress(), artNet.listenPort(),
				artNet.pollInterval());
	}

	@Bean // closed automatically (AutoCloseable) when discovery is running
	ArtNetNodeDirectory artNetNodeDirectory(LumenProperties properties, ArtNetSettings settings, Clock clock,
			StateEvents stateEvents) {
		if (!properties.artnet().enabled()) {
			return ArtNetNodeDirectory.NONE;
		}
		var discovery = new ArtNetDiscovery(settings, clock, () -> stateEvents.publish(StateTopic.NODES));
		discovery.start();
		log.info("Art-Net discovery: ArtPoll every {} to {}, listening on port {}", settings.pollInterval(),
				settings.broadcastAddress(), settings.listenPort());
		return discovery;
	}

	@Bean
	ArtNetNodeService artNetNodeService(ArtNetNodeDirectory directory, Clock clock) {
		return new ArtNetNodeService(directory, clock);
	}

	@Bean
	DmxOutputPort dmxOutput(LumenProperties properties, ArtNetSettings settings, ArtNetNodeDirectory directory,
			Clock clock) {
		if (!properties.artnet().enabled()) {
			log.info("Art-Net output is disabled");
			return DmxOutputPort.NONE;
		}
		log.info("Art-Net output from {} to port {}, broadcast address {}", settings.bindAddress(), settings.port(),
				settings.broadcastAddress());
		return new ArtNetDmxOutput(settings, directory, clock);
	}

	@Bean
	StateEvents stateEvents() {
		return new StateEvents();
	}

	@Bean
	OutputEngine outputEngine(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			DmxOutputPort dmxOutput, StateEvents stateEvents) {
		var engine = new OutputEngine(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), library, dmxOutput, stateEvents);
		engine.patchChanged();
		return engine;
	}

	@Bean(destroyMethod = "close")
	RenderLoop renderLoop(OutputEngine engine, LumenProperties properties) {
		var loop = new RenderLoop(engine, properties.output().rateHz());
		loop.start();
		log.info("Render loop running at {} Hz", properties.output().rateHz());
		return loop;
	}
}
