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
import java.util.Arrays;
import java.util.HexFormat;
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
