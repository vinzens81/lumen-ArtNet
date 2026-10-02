package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end over loopback: a simulated ESP node answers ArtPoll, gets discovered and then receives ArtDmx
 * by unicast for its universe only.
 */
class ArtNetDiscoveryTest {

	private DatagramSocket node;
	private ArtNetDiscovery discovery;
	private ArtNetDmxOutput output;
	private final AtomicInteger changes = new AtomicInteger();

	@BeforeEach
	void setUp() throws IOException {
		node = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		node.setSoTimeout(3_000);
		int listenPort;
		try (var probe = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
			listenPort = probe.getLocalPort();
		}
		var settings = new ArtNetSettings("127.0.0.1", node.getLocalPort(), "127.0.0.1", listenPort,
				Duration.ofMillis(100));
		discovery = new ArtNetDiscovery(settings, Clock.systemUTC(), changes::incrementAndGet);
		output = new ArtNetDmxOutput(settings, discovery, Clock.systemUTC());
		discovery.start();
	}

	@AfterEach
	void tearDown() {
		discovery.close();
		output.close();
		node.close();
	}

	@Test
	void discoveredNodeReceivesItsUniverseByUnicast() throws Exception {
		var poll = receive(ArtPollPackets.OP_POLL);
		var reply = EspNodeSimulator.pollReply(new int[] {127, 0, 0, 1}, 3, "Test dimmer");
		node.send(new DatagramPacket(reply, reply.length, poll.getAddress(), poll.getPort()));

		waitFor(() -> !discovery.nodes().isEmpty());
		assertThat(discovery.nodes()).singleElement().satisfies(n -> {
			assertThat(n.shortName()).isEqualTo("Test dimmer");
			assertThat(n.portAddresses()).containsExactly(3);
		});
		assertThat(changes.get()).isGreaterThanOrEqualTo(1);

		var mine = universe(3);
		var other = universe(4);
		assertThat(output.destinations(mine.spec().target(), 3)).containsExactly("127.0.0.1");
		assertThat(output.destinations(other.spec().target(), 4)).isEmpty();

		output.send(mine, DmxFrame.builder().set(1, 200).build());
		var dmx = receive(ArtDmxPacket.OP_DMX);
		assertThat(Byte.toUnsignedInt(dmx.getData()[18])).isEqualTo(200);
		assertThat(dmx.getData()[14]).as("SubUni").isEqualTo((byte) 3);
	}

	@Test
	void manualAddressesAreAddedToDiscoveredOnes() {
		var target = new ArtNetTarget.Unicast(true, List.of("10.0.0.9"));

		assertThat(output.destinations(target, 7)).containsExactly("10.0.0.9");
		assertThat(output.destinations(new ArtNetTarget.Broadcast(), 7)).containsExactly("127.0.0.1");
	}

	/** Receives packets until one with the given OpCode arrives. */
	private DatagramPacket receive(int opCode) throws IOException {
		while (true) {
			var packet = new DatagramPacket(new byte[1024], 1024);
			node.receive(packet);
			var data = Arrays.copyOf(packet.getData(), packet.getLength());
			if (ArtPollPackets.opCode(data, data.length) == opCode) {
				packet.setData(data);
				return packet;
			}
		}
	}

	private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
		var deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertThat(condition.getAsBoolean()).as("condition met within 3 s").isTrue();
	}

	private static Universe universe(int portAddress) {
		return new Universe(portAddress, 1, new UniverseSpec(portAddress, Optional.empty(),
				ArtNetAddress.fromPortAddress(portAddress), ArtNetTarget.auto()));
	}
}
