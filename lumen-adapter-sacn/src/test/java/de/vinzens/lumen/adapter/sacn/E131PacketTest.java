package de.vinzens.lumen.adapter.sacn;

import de.vinzens.lumen.domain.output.DmxFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class E131PacketTest {

	private static final UUID CID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
	private static final HexFormat HEX = HexFormat.of();

	@Test
	void encodesDataPacketByteExact() {
		var frame = DmxFrame.builder().set(1, 255).set(512, 7).build();

		var packet = E131Packet.data(CID, "Lumen", 120, 42, 0, 258, frame);

		assertThat(packet).hasSize(638);
		// root layer: preamble 0x0010 | post-amble 0 | "ASC-E1.17\0\0\0" | flags+length 0x7000|622 | vector 4 | CID
		assertThat(hex(packet, 0, 38))
				.isEqualTo("00100000" + "4153432d45312e3137000000" + "726e" + "00000004" + "00112233445566778899aabbccddeeff");
		// framing layer: flags+length 0x7000|600 | vector 2 | source name (64 bytes)
		assertThat(hex(packet, 38, 44)).isEqualTo("7258" + "00000002");
		assertThat(Arrays.copyOfRange(packet, 44, 49)).asString().isEqualTo("Lumen");
		assertThat(packet[49]).as("name is null-terminated and padded").isZero();
		// priority 120 | sync address 0 | sequence 42 | options 0 | universe 258
		assertThat(hex(packet, 108, 115)).isEqualTo("78" + "0000" + "2a" + "00" + "0102");
		// DMP layer: flags+length 0x7000|523 | vector 2 | 0xa1 | first address 0 | increment 1 | 513 values | start 0
		assertThat(hex(packet, 115, 126)).isEqualTo("720b" + "02" + "a1" + "0000" + "0001" + "0201" + "00");
		assertThat(Byte.toUnsignedInt(packet[126])).as("channel 1").isEqualTo(255);
		assertThat(packet[637]).as("channel 512").isEqualTo((byte) 7);
	}

	@Test
	void marksTerminatedStreams() {
		var packet = E131Packet.data(CID, "Lumen", 100, 0, E131Packet.OPTION_TERMINATED, 1, DmxFrame.BLACK);

		assertThat(packet[112]).isEqualTo((byte) 0x40);
	}

	@Test
	void cutsLongSourceNamesWithoutBreakingACharacter() {
		var name = "a".repeat(62) + "ü"; // ü takes two bytes, only one would fit

		var packet = E131Packet.data(CID, name, 100, 0, 0, 1, DmxFrame.BLACK);

		assertThat(Arrays.copyOfRange(packet, 44, 108)).startsWith("a".repeat(62).getBytes());
		assertThat(packet[44 + 62]).isZero();
	}

	@Test
	void encodesDiscoveryPacketWithSortedUniverses() {
		var packets = E131Packet.discovery(CID, "Lumen", List.of(7, 1, 7, 300));

		assertThat(packets).singleElement().satisfies(packet -> {
			assertThat(packet).hasSize(126);
			// root: flags+length 0x7000|110, vector 8 (extended)
			assertThat(hex(packet, 16, 22)).isEqualTo("706e" + "00000008");
			// framing: flags+length 0x7000|88, vector 2 (discovery), reserved after the name
			assertThat(hex(packet, 38, 44)).isEqualTo("7058" + "00000002");
			assertThat(hex(packet, 108, 112)).isEqualTo("00000000");
			// discovery layer: flags+length 0x7000|14, vector 1, page 0 of last page 0, universes 1, 7, 300
			assertThat(hex(packet, 112, 126)).isEqualTo("700e" + "00000001" + "00" + "00" + "0001" + "0007" + "012c");
		});
	}

	@Test
	void splitsDiscoveryIntoPagesOf512() {
		var packets = E131Packet.discovery(CID, "Lumen", IntStream.rangeClosed(1, 600).boxed().toList());

		assertThat(packets).hasSize(2);
		assertThat(packets.get(0)).hasSize(120 + 2 * 512);
		assertThat(hex(packets.get(1), 118, 122)).as("page 1 of last page 1, first universe 513")
				.isEqualTo("0101" + "0201");
	}

	private static String hex(byte[] bytes, int from, int to) {
		return HEX.formatHex(Arrays.copyOfRange(bytes, from, to));
	}
}
