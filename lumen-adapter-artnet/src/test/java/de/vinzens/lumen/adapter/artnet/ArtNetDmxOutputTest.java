package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetNode;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.LocalNetwork;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArtNetDmxOutputTest {

	private DatagramSocket receiver;
	private ArtNetDmxOutput output;

	@BeforeEach
	void setUp() throws IOException {
		receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		receiver.setSoTimeout(2_000);
		output = new ArtNetDmxOutput(new ArtNetSettings("127.0.0.1", receiver.getLocalPort(), "127.255.255.255"));
	}

	@AfterEach
	void tearDown() {
		output.close();
		receiver.close();
	}

	@Test
	void encodesArtDmxHeaderByteExact() {
		var frame = DmxFrame.builder().set(1, 255).set(512, 7).build();

		var packet = ArtDmxPacket.encode(42, new ArtNetAddress(1, 2, 3), frame);

		assertThat(packet).hasSize(530);
		assertThat(HexFormat.of().formatHex(Arrays.copyOf(packet, 18)))
				// "Art-Net\0" | OpCode 0x5000 LE | ProtVer 14 BE | seq 42 | phys 0 | SubUni 0x23 | Net 1 | len 512 BE
				.isEqualTo("4172742d4e6574000050000e2a0023010200");
		assertThat(Byte.toUnsignedInt(packet[18])).as("channel 1").isEqualTo(255);
		assertThat(packet[529]).as("channel 512").isEqualTo((byte) 7);
	}

	@Test
	void sendsUnicastPacketsWithIncreasingSequence() throws IOException {
		var universe = universe(new ArtNetAddress(0, 0, 5), ArtNetTarget.parse("127.0.0.1"));
		var frame = DmxFrame.builder().set(10, 200).build();

		output.send(universe, frame);
		output.send(universe, frame);

		var first = receive();
		var second = receive();
		assertThat(first).hasSize(530);
		assertThat(first[14]).as("SubUni").isEqualTo((byte) 5);
		assertThat(Byte.toUnsignedInt(first[18 + 9])).as("channel 10").isEqualTo(200);
		assertThat(first[12]).as("sequence").isEqualTo((byte) 1);
		assertThat(second[12]).as("sequence").isEqualTo((byte) 2);
		assertThat(output.counters()).isEqualTo(new DmxOutputPort.Counters(true, 2, 0));
	}

	@Test
	void sequenceWrapsFrom255To1() throws IOException {
		var universe = universe(new ArtNetAddress(0, 0, 0), ArtNetTarget.parse("127.0.0.1"));
		for (int i = 0; i < 256; i++) {
			output.send(universe, DmxFrame.BLACK);
		}

		byte[] last = null;
		for (int i = 0; i < 256; i++) {
			last = receive();
		}
		assertThat(last[12]).isEqualTo((byte) 1);
	}

	@Test
	void universeBoundToAnInterfaceUsesOnlyItsNetwork() {
		var now = Instant.parse("2026-10-05T20:00:00Z");
		var wifiNode = new ArtNetNode("192.168.0.150", 1, "ESP", "ESP", "", List.of(0), "", now);
		var stageNode = new ArtNetNode("2.0.0.21", 1, "Stage", "Stage", "", List.of(0), "", now);
		var networks = List.of(new LocalNetwork("en0", "192.168.0.23", 24), new LocalNetwork("en7", "2.0.0.10", 8));
		try (var bound = new ArtNetDmxOutput(new ArtNetSettings("127.0.0.1", 6454, "255.255.255.255"),
				() -> List.of(wifiNode, stageNode), () -> networks, Clock.fixed(now, ZoneOffset.UTC))) {
			assertThat(bound.destinations(spec(ArtNetTarget.auto(), Optional.empty())))
					.containsExactly("192.168.0.150", "2.0.0.21");
			assertThat(bound.destinations(spec(ArtNetTarget.auto(), Optional.of("en7")))).containsExactly("2.0.0.21");
			assertThat(bound.destinations(spec(ArtNetTarget.parse("auto,10.9.9.9"), Optional.of("en0"))))
					.as("manual addresses stay").containsExactly("192.168.0.150", "10.9.9.9");
			assertThat(bound.destinations(spec(new ArtNetTarget.Broadcast(), Optional.of("en7"))))
					.containsExactly("2.255.255.255");
			assertThat(bound.destinations(spec(new ArtNetTarget.Broadcast(), Optional.empty())))
					.containsExactly("255.255.255.255");
			assertThat(bound.destinations(spec(ArtNetTarget.auto(), Optional.of("en9"))))
					.as("interface missing, e.g. adapter unplugged").isEmpty();
		}
	}

	private static UniverseSpec spec(ArtNetTarget target, Optional<String> networkInterface) {
		return new UniverseSpec(1, Optional.empty(), new ArtNetAddress(0, 0, 0), target, networkInterface);
	}

	private byte[] receive() throws IOException {
		var buffer = new byte[1024];
		var packet = new DatagramPacket(buffer, buffer.length);
		receiver.receive(packet);
		return Arrays.copyOf(packet.getData(), packet.getLength());
	}

	private static Universe universe(ArtNetAddress address, ArtNetTarget target) {
		return new Universe(1, 1, new UniverseSpec(1, Optional.empty(), address, target));
	}
}
