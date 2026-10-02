package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.artnet.ArtNetDmxOutput;
import de.vinzens.lumen.adapter.artnet.ArtNetSettings;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.output.RenderLoop;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OutputConfiguration {

	private static final Logger log = LoggerFactory.getLogger(OutputConfiguration.class);

	@Bean
	DmxOutputPort dmxOutput(LumenProperties properties) {
		var artNet = properties.artnet();
		if (!artNet.enabled()) {
			log.info("Art-Net output is disabled");
			return DmxOutputPort.NONE;
		}
		log.info("Art-Net output from {} to port {}, broadcast address {}", artNet.bindAddress(), artNet.port(),
				artNet.broadcastAddress());
		return new ArtNetDmxOutput(new ArtNetSettings(artNet.bindAddress(), artNet.port(), artNet.broadcastAddress()));
	}

	@Bean
	OutputEngine outputEngine(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			DmxOutputPort dmxOutput) {
		var engine = new OutputEngine(repositories.projects(), repositories.universes(), repositories.fixtures(),
				library, dmxOutput);
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
