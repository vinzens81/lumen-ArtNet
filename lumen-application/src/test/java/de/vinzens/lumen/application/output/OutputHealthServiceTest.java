package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase.Level;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeCheck;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeState;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.Universe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OutputHealthServiceTest {

	private final FakeOutput output = new FakeOutput();
	private final List<StateTopic> published = new ArrayList<>();
	private final RenderLoop loop = new RenderLoop(() -> {
	}, 40);
	private long nanos;
	private int universes = 1;
	private List<NodeCheck> nodes = List.of();

	@AfterEach
	void tearDown() {
		loop.close();
	}

	@Test
	void reportsRatesOfTheLastSecond() throws InterruptedException {
		var health = service();
		loop.start();
		health.sample();
		Thread.sleep(1000);
		output.packets = 40;
		nanos += TimeUnit.SECONDS.toNanos(1);

		health.sample();

		var result = health.health();
		assertThat(result.level()).isEqualTo(Level.OK);
		assertThat(result.problems()).isEmpty();
		assertThat(result.packetsPerSecond()).isEqualTo(40.0);
		assertThat(result.renderRateHz()).isBetween(30.0, 45.0);
		assertThat(result.artNetActive()).isTrue();
		assertThat(published).containsExactly(StateTopic.HEALTH);
	}

	@Test
	void warnsWhenNoPacketsAreSentOrSendingFails() throws InterruptedException {
		var health = service();
		loop.start();
		health.sample();
		Thread.sleep(300);
		nanos += TimeUnit.MILLISECONDS.toNanos(300);
		health.sample();
		assertThat(health.health().level()).isEqualTo(Level.WARNING);
		assertThat(health.health().problems()).anyMatch(p -> p.contains("No DMX packets"));

		output.errors = 3;
		nanos += TimeUnit.MILLISECONDS.toNanos(300);
		Thread.sleep(300);
		health.sample();
		assertThat(health.health().problems()).anyMatch(p -> p.contains("cannot be sent"));

		universes = 0;
		nanos += TimeUnit.MILLISECONDS.toNanos(300);
		Thread.sleep(300);
		health.sample();
		assertThat(health.health().level()).as("nothing to send").isEqualTo(Level.OK);
	}

	@Test
	void missingNodesAreWarnings() throws InterruptedException {
		var health = service();
		loop.start();
		output.packets = 0;
		health.sample();
		nodes = List.of(
				new NodeCheck("192.168.0.150", "ESP dimmer", NodeState.OFFLINE, List.of(1), Optional.empty()),
				new NodeCheck("10.0.0.5", "", NodeState.UNCHECKED, List.of(2, 3), Optional.empty()),
				new NodeCheck("10.0.0.6", "", NodeState.OFFLINE, List.of(2, 3), Optional.empty()),
				new NodeCheck("2.0.0.21", "Stage", NodeState.ONLINE, List.of(4), Optional.empty()));
		Thread.sleep(300);
		output.packets = 12;
		nanos += TimeUnit.MILLISECONDS.toNanos(300);

		health.sample();

		assertThat(health.health().level()).isEqualTo(Level.WARNING);
		assertThat(health.health().problems()).containsExactly(
				"Node ESP dimmer (192.168.0.150) does not answer – universe 1 may stay dark",
				"Node 10.0.0.6 does not answer – universes 2, 3 may stay dark");
		assertThat(health.health().nodes()).hasSize(4);
	}

	@Test
	void stoppedLoopIsAnError() {
		var health = service();
		health.sample();
		nanos += TimeUnit.SECONDS.toNanos(1);
		health.sample();

		assertThat(health.health().level()).isEqualTo(Level.ERROR);
		assertThat(health.health().problems()).anyMatch(p -> p.contains("not running"));
	}

	private OutputHealthService service() {
		var events = new StateEvents();
		events.subscribe(published::add);
		return new OutputHealthService(loop, output, () -> universes, () -> nodes, events, () -> nanos);
	}

	private static final class FakeOutput implements DmxOutputPort {

		long packets;
		long errors;

		@Override
		public void send(Universe universe, DmxFrame frame) {
		}

		@Override
		public Counters counters() {
			return new Counters(true, packets, errors);
		}
	}
}
