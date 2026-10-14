package de.vinzens.lumen.adapter.sacn;

import de.vinzens.lumen.domain.output.DmxFrame;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Encodes sACN packets (ANSI E1.31-2018): data packets carrying a full 512-channel DMX frame, and universe discovery
 * packets announcing which universes this source sends.
 */
final class E131Packet {

	static final int DATA_LENGTH = 126 + DmxFrame.SIZE;
	/** Stream_Terminated: this source stops sending the universe */
	static final int OPTION_TERMINATED = 0x40;
	/** universes listed on one discovery page */
	static final int DISCOVERY_PAGE_SIZE = 512;
	/** the multicast group of universe 64214, which carries discovery packets */
	static final String DISCOVERY_ADDRESS = "239.255.250.214";

	private static final byte[] ACN_PACKET_ID = {0x41, 0x53, 0x43, 0x2d, 0x45, 0x31, 0x2e, 0x31, 0x37, 0, 0, 0};
	private static final int VECTOR_ROOT_E131_DATA = 0x04;
	private static final int VECTOR_ROOT_E131_EXTENDED = 0x08;
	private static final int VECTOR_E131_DATA_PACKET = 0x02;
	private static final int VECTOR_E131_EXTENDED_DISCOVERY = 0x02;
	private static final int VECTOR_DMP_SET_PROPERTY = 0x02;
	private static final int VECTOR_UNIVERSE_DISCOVERY_UNIVERSE_LIST = 0x01;
	private static final int SOURCE_NAME_LENGTH = 64;

	private E131Packet() {
	}

	/**
	 * @param sequence 0–255, increased by one per packet of the universe
	 * @param options  0, or {@link #OPTION_TERMINATED}
	 */
	static byte[] data(UUID cid, String sourceName, int priority, int sequence, int options, int universe,
			DmxFrame frame) {
		var buffer = ByteBuffer.allocate(DATA_LENGTH);
		rootLayer(buffer, VECTOR_ROOT_E131_DATA, cid);
		// framing layer
		flagsAndLength(buffer, DATA_LENGTH - 38);
		buffer.putInt(VECTOR_E131_DATA_PACKET);
		buffer.put(sourceName(sourceName));
		buffer.put((byte) priority);
		buffer.putShort((short) 0); // synchronization address: no synchronization
		buffer.put((byte) sequence);
		buffer.put((byte) options);
		buffer.putShort((short) universe);
		// DMP layer
		flagsAndLength(buffer, DATA_LENGTH - 115);
		buffer.put((byte) VECTOR_DMP_SET_PROPERTY);
		buffer.put((byte) 0xa1); // address type and data type
		buffer.putShort((short) 0); // first property address
		buffer.putShort((short) 1); // address increment
		buffer.putShort((short) (DmxFrame.SIZE + 1)); // property value count: start code + slots
		buffer.put((byte) 0); // DMX start code
		buffer.put(frame.toByteArray());
		return buffer.array();
	}

	/** One discovery packet per page of up to {@value #DISCOVERY_PAGE_SIZE} universes (sorted, as required). */
	static List<byte[]> discovery(UUID cid, String sourceName, List<Integer> universes) {
		var sorted = universes.stream().distinct().sorted().toList();
		var pages = Math.max(1, (sorted.size() + DISCOVERY_PAGE_SIZE - 1) / DISCOVERY_PAGE_SIZE);
		return IntStream.range(0, pages).mapToObj(page -> {
			var list = sorted.subList(page * DISCOVERY_PAGE_SIZE,
					Math.min(sorted.size(), (page + 1) * DISCOVERY_PAGE_SIZE));
			var length = 120 + 2 * list.size();
			var buffer = ByteBuffer.allocate(length);
			rootLayer(buffer, VECTOR_ROOT_E131_EXTENDED, cid);
			// framing layer
			flagsAndLength(buffer, length - 38);
			buffer.putInt(VECTOR_E131_EXTENDED_DISCOVERY);
			buffer.put(sourceName(sourceName));
			buffer.putInt(0); // reserved
			// universe discovery layer
			flagsAndLength(buffer, length - 112);
			buffer.putInt(VECTOR_UNIVERSE_DISCOVERY_UNIVERSE_LIST);
			buffer.put((byte) page);
			buffer.put((byte) (pages - 1));
			list.forEach(universe -> buffer.putShort(universe.shortValue()));
			return buffer.array();
		}).toList();
	}

	private static void rootLayer(ByteBuffer buffer, int vector, UUID cid) {
		buffer.putShort((short) 0x0010); // preamble size
		buffer.putShort((short) 0); // post-amble size
		buffer.put(ACN_PACKET_ID);
		flagsAndLength(buffer, buffer.capacity() - 16);
		buffer.putInt(vector);
		buffer.putLong(cid.getMostSignificantBits());
		buffer.putLong(cid.getLeastSignificantBits());
	}

	/** Flags 0x7 in the high 4 bits, the length of the layer from here to the end in the low 12. */
	private static void flagsAndLength(ByteBuffer buffer, int length) {
		buffer.putShort((short) (0x7000 | length));
	}

	/** UTF-8, null-terminated, at most 63 bytes without cutting a character. */
	private static byte[] sourceName(String name) {
		var bytes = name.getBytes(StandardCharsets.UTF_8);
		var length = Math.min(bytes.length, SOURCE_NAME_LENGTH - 1);
		while (length > 0 && length < bytes.length && (bytes[length] & 0xC0) == 0x80) {
			length--; // a continuation byte: the character would be cut
		}
		return Arrays.copyOf(Arrays.copyOf(bytes, length), SOURCE_NAME_LENGTH);
	}
}
