package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetAddress;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Encodes ArtDmx packets (Art-Net 4, OpCode 0x5000) carrying a full 512-channel DMX frame.
 */
final class ArtDmxPacket {

	static final int OP_DMX = 0x5000;
	static final int PROTOCOL_VERSION = 14;
	static final int LENGTH = 18 + DmxFrame.SIZE;

	private static final byte[] ID = "Art-Net\0".getBytes(StandardCharsets.US_ASCII);

	private ArtDmxPacket() {
	}

	/**
	 * @param sequence 1–255 to let receivers reorder packets, 0 disables sequencing
	 */
	static byte[] encode(int sequence, ArtNetAddress address, DmxFrame frame) {
		var buffer = ByteBuffer.allocate(LENGTH);
		buffer.put(ID);
		buffer.order(ByteOrder.LITTLE_ENDIAN).putShort((short) OP_DMX);
		buffer.order(ByteOrder.BIG_ENDIAN).putShort((short) PROTOCOL_VERSION);
		buffer.put((byte) sequence);
		buffer.put((byte) 0); // physical input port, informational only
		var portAddress = address.portAddress();
		buffer.put((byte) (portAddress & 0xFF)); // SubUni: subnet (high nibble) + universe (low nibble)
		buffer.put((byte) (portAddress >> 8)); // Net
		buffer.putShort((short) DmxFrame.SIZE);
		buffer.put(frame.toByteArray());
		return buffer.array();
	}
}
