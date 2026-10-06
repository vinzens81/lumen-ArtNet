package de.vinzens.lumen.config;

import de.vinzens.lumen.adapter.artnet.ArtNetDiscovery;
import de.vinzens.lumen.adapter.artnet.ArtNetDmxOutput;
import de.vinzens.lumen.adapter.artnet.ArtNetSettings;
import de.vinzens.lumen.adapter.artnet.SystemNetworks;
import de.vinzens.lumen.adapter.sqlite.SqliteRepositories;
import de.vinzens.lumen.application.composition.CompositionPreviewService;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.output.ArtNetNodeService;
import de.vinzens.lumen.application.output.NodeWatchService;
import de.vinzens.lumen.application.output.OperatingModeKeeper;
import de.vinzens.lumen.application.output.OutputHealthService;
import de.vinzens.lumen.application.output.OutputEngine;
import de.vinzens.lumen.application.output.RenderLoop;
import de.vinzens.lumen.application.output.port.out.ArtNetNodeDirectory;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.application.output.port.out.LocalNetworks;
import de.vinzens.lumen.application.party.PartyService;
import de.vinzens.lumen.application.playback.PlaybackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
class OutputConfiguration {

	private static final Logger log = LoggerFactory.getLogger(OutputConfiguration.class);

	@Bean
	ArtNetSettings artNetSettings(LumenProperties properties) {
		var artNet = properties.artnet();
		return new ArtNetSettings(artNet.bindAddress(), artNet.port(), artNet.broadcastAddress(), artNet.listenPort(),
				artNet.pollInterval());
	}

	/** Read even with Art-Net off, so universes can be bound to an interface in test mode too. */
	@Bean
	LocalNetworks localNetworks(Clock clock) {
		return new SystemNetworks(clock);
	}

	@Bean // closed automatically (AutoCloseable) when discovery is running
	ArtNetNodeDirectory artNetNodeDirectory(LumenProperties properties, ArtNetSettings settings,
			LocalNetworks networks, Clock clock, StateEvents stateEvents) {
		if (!properties.artnet().enabled()) {
			return ArtNetNodeDirectory.NONE;
		}
		var discovery = new ArtNetDiscovery(settings, networks, clock, () -> stateEvents.publish(StateTopic.NODES));
		discovery.start();
		log.info("Art-Net discovery: ArtPoll every {} to {} and the local networks {}, listening on port {}",
				settings.pollInterval(), settings.broadcastAddress(), networks.networks(), settings.listenPort());
		return discovery;
	}

	@Bean
	ArtNetNodeService artNetNodeService(ArtNetNodeDirectory directory, LocalNetworks networks, Clock clock) {
		return new ArtNetNodeService(directory, networks, clock);
	}

	@Bean
	DmxOutputPort dmxOutput(LumenProperties properties, ArtNetSettings settings, ArtNetNodeDirectory directory,
			LocalNetworks networks, Clock clock) {
		if (!properties.artnet().enabled()) {
			log.info("Art-Net output is disabled");
			return DmxOutputPort.NONE;
		}
		log.info("Art-Net output from {} to port {}, broadcast address {}", settings.bindAddress(), settings.port(),
				settings.broadcastAddress());
		return new ArtNetDmxOutput(settings, directory, networks, clock);
	}

	@Bean
	StateEvents stateEvents() {
		return new StateEvents();
	}

	@Bean
	OutputEngine outputEngine(SqliteRepositories repositories, QueryFixtureLibraryUseCase library,
			DmxOutputPort dmxOutput, StateEvents stateEvents, Clock clock) {
		var engine = new OutputEngine(repositories.projects(), repositories.universes(), repositories.fixtures(),
				repositories.groups(), library, dmxOutput, stateEvents, clock);
		engine.patchChanged();
		return engine;
	}

	@Bean
	OperatingModeKeeper operatingModeKeeper(OutputEngine outputEngine, SqliteRepositories repositories,
			StateEvents stateEvents) {
		var keeper = new OperatingModeKeeper(outputEngine, repositories.settings(), stateEvents);
		log.info("Operating mode: {}", outputEngine.mode());
		return keeper;
	}

	/** Saves the playback state off the render thread; pending writes are finished on shutdown. */
	@Bean(destroyMethod = "close")
	ExecutorService playbackStateWriter() {
		return Executors.newSingleThreadExecutor(runnable -> {
			var thread = new Thread(runnable, "lumen-playback-state");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Bean
	PlaybackService playbackService(SqliteRepositories repositories, CompositionResolver compositionResolver,
			ExecutorService playbackStateWriter, Clock clock, StateEvents stateEvents, OutputEngine outputEngine) {
		var playback = new PlaybackService(repositories.projects(), repositories.scenes(), compositionResolver,
				repositories.playbackStates(), repositories.faders(), playbackStateWriter, clock, stateEvents);
		outputEngine.addLayerSource(playback);
		playback.status().resumeSceneId().ifPresent(sceneId ->
				log.info("Show can be resumed at scene id {}", sceneId));
		return playback;
	}

	@Bean
	CompositionPreviewService compositionPreviewService(SqliteRepositories repositories,
			CompositionResolver compositionResolver, OutputEngine outputEngine, Clock clock, StateEvents stateEvents) {
		var preview = new CompositionPreviewService(repositories.compositions(), repositories.scenes(),
				repositories.projects(), compositionResolver, outputEngine, clock, stateEvents);
		outputEngine.addLayerSource(preview);
		return preview;
	}

	@Bean
	PartyService partyService(SqliteRepositories repositories, CompositionResolver compositionResolver,
			OutputEngine outputEngine, Clock clock, StateEvents stateEvents) {
		var party = new PartyService(repositories.projects(), repositories.partyMatrices(), compositionResolver,
				outputEngine, clock, stateEvents);
		outputEngine.addLayerSource(party);
		return party;
	}

	@Bean(destroyMethod = "close")
	// depends on the playback, the preview and party mode so their layers are registered before the first tick
	RenderLoop renderLoop(PlaybackService playbackService, CompositionPreviewService compositionPreviewService,
			PartyService partyService, OutputEngine engine, LumenProperties properties) {
		var loop = new RenderLoop(engine, properties.output().rateHz());
		loop.start();
		log.info("Render loop running at {} Hz", properties.output().rateHz());
		return loop;
	}

	@Bean
	NodeWatchService nodeWatchService(SqliteRepositories repositories, ArtNetNodeDirectory directory, Clock clock,
			StateEvents stateEvents) {
		return new NodeWatchService(repositories.projects(), repositories.universes(), repositories.expectedNodes(),
				directory, clock, stateEvents);
	}

	@Bean(destroyMethod = "close")
	OutputHealthService outputHealthService(RenderLoop renderLoop, DmxOutputPort dmxOutput, OutputEngine engine,
			NodeWatchService nodeWatch, StateEvents stateEvents) {
		var health = new OutputHealthService(renderLoop, dmxOutput, engine::universeCount, nodeWatch::check,
				stateEvents);
		health.start();
		return health;
	}
}
