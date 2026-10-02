package de.vinzens.lumen.adapter.artnet;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class ArtPollPacketsTest {

	@Test
	void encodesArtPoll() {
		assertThat(HexFormat.of().formatHex(ArtPollPackets.poll())).isEqualTo("4172742d4e657400" + "0020" + "000e" + "02" + "00");
	}

	@Test
	void parsesTheReplyOfTheEspDimmer() throws Exception {
		var reply = EspNodeSimulator.pollReply(new int[] {192, 168, 1, 77}, 0x123, "Stage dimmer");
		var now = Instant.parse("2026-10-02T12:00:00Z");

		var node = ArtPollPackets.parseReply(reply, reply.length, InetAddress.getLoopbackAddress(), now).orElseThrow();

		assertThat(node.ipAddress()).isEqualTo("192.168.1.77");
		assertThat(node.shortName()).isEqualTo("Stage dimmer");
		assertThat(node.longName()).isEqualTo("Stage dimmer (ESP8266 Art-Net Dimmer)");
		assertThat(node.nodeReport()).isEqualTo("#0001 [0000] DMX OK");
		assertThat(node.portAddresses()).containsExactly(0x123);
		assertThat(node.macAddress()).isEqualTo("24:0a:c4:12:34:56");
		assertThat(node.lastSeen()).isEqualTo(now);
	}

	@Test
	void ignoresOtherPacketsAndShortReplies() throws Exception {
		var sender = InetAddress.getLoopbackAddress();
		var poll = ArtPollPackets.poll();
		var reply = EspNodeSimulator.pollReply(new int[] {10, 0, 0, 1}, 0, "x");

		assertThat(ArtPollPackets.parseReply(poll, poll.length, sender, Instant.EPOCH)).isEmpty();
		assertThat(ArtPollPackets.parseReply(reply, 100, sender, Instant.EPOCH)).isEmpty();
		assertThat(ArtPollPackets.parseReply(new byte[20], 20, sender, Instant.EPOCH)).isEmpty();
	}

	@Test
	void usesTheSenderIfTheReplyHasNoIpAddress() throws Exception {
		var reply = EspNodeSimulator.pollReply(new int[] {0, 0, 0, 0}, 0, "x");

		var node = ArtPollPackets.parseReply(reply, reply.length, InetAddress.getByName("10.1.2.3"), Instant.EPOCH);

		assertThat(node).get().extracting(n -> n.ipAddress()).isEqualTo("10.1.2.3");
	}
}
