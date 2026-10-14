package de.vinzens.lumen.adapter.sacn;

import de.vinzens.lumen.application.output.port.out.LocalNetworks;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.OutputProtocol;
import de.vinzens.lumen.domain.project.SacnOutput;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SacnDmxOutputTest {

	private static final UUID CID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

	private DatagramSocket receiver;
	private SacnDmxOutput output;

	@BeforeEach
	void setUp() throws IOException {
		receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		receiver.setSoTimeout(2_000);
		output = new SacnDmxOutput(new SacnSettings("127.0.0.1", receiver.getLocalPort(), "Lumen test", CID),
				LocalNetworks.NONE, Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"), ZoneOffset.UTC));
	}

	@AfterEach
	void tearDown() {
		output.close();
		receiver.close();
	}

	@Test
	void sendsByUnicastWithRisingSequence() throws IOException {
		var universe = universe(OutputProtocol.SACN, new SacnOutput(5, 150, List.of("127.0.0.1")), Optional.empty());
		var frame = DmxFrame.builder().set(1, 200).build();

		output.send(universe, frame);
		output.send(universe, frame);

		var first = receive();
		var second = receive();
		assertThat(first).hasSize(E131Packet.DATA_LENGTH);
		assertThat(Byte.toUnsignedInt(first[108])).as("priority").isEqualTo(150);
		assertThat(first[111]).as("sequence").isEqualTo((byte) 0);
		assertThat(second[111]).isEqualTo((byte) 1);
		assertThat(first[114]).as("universe").isEqualTo((byte) 5);
		assertThat(Byte.toUnsignedInt(first[126])).as("channel 1").isEqualTo(200);
		assertThat(output.counters().packetsSent()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void leavesArtNetOnlyUniversesAlone() {
		output.send(universe(OutputProtocol.ART_NET, new SacnOutput(5, 100, List.of("127.0.0.1")), Optional.empty()),
				DmxFrame.BLACK);

		assertThatThrownBy(this::receive).isInstanceOf(SocketTimeoutException.class);
	}

	@Test
	void skipsUniversesWhoseInterfaceIsMissing() {
		output.send(universe(OutputProtocol.BOTH, new SacnOutput(5, 100, List.of("127.0.0.1")), Optional.of("en99")),
				DmxFrame.BLACK);

		assertThatThrownBy(this::receive).isInstanceOf(SocketTimeoutException.class);
	}

	@Test
	void terminatesTheStreamsThreeTimesOnClose() throws IOException {
		var universe = universe(OutputProtocol.SACN, new SacnOutput(5, 100, List.of("127.0.0.1")), Optional.empty());
		output.send(universe, DmxFrame.BLACK);
		receive();

		output.close();

		for (int sequence = 1; sequence <= 3; sequence++) {
			var packet = receive();
			assertThat(packet[112]).as("Stream_Terminated").isEqualTo((byte) 0x40);
			assertThat(packet[111]).isEqualTo((byte) sequence);
		}
	}

	private byte[] receive() throws IOException {
		var buffer = new byte[1024];
		var packet = new DatagramPacket(buffer, buffer.length);
		receiver.receive(packet);
		return Arrays.copyOf(buffer, packet.getLength());
	}

	private static Universe universe(OutputProtocol protocol, SacnOutput sacn, Optional<String> networkInterface) {
		return new Universe(1, 1, new UniverseSpec(1, Optional.empty(), new ArtNetAddress(0, 0, 0),
				ArtNetTarget.auto(), networkInterface, protocol, sacn));
	}
}
