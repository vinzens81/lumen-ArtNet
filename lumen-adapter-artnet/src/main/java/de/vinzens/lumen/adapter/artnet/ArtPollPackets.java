package de.vinzens.lumen.adapter.artnet;

import de.vinzens.lumen.domain.project.ArtNetNode;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Encodes ArtPoll (OpCode 0x2000) and decodes ArtPollReply (OpCode 0x2100) packets.
 */
final class ArtPollPackets {

	static final int OP_POLL = 0x2000;
	static final int OP_POLL_REPLY = 0x2100;
	/** Art-Net 3 replies are at least 207 bytes; Art-Net 4 adds fields up to 239 bytes. */
	static final int MIN_REPLY_LENGTH = 207;

	private static final byte[] ID = "Art-Net\0".getBytes(StandardCharsets.US_ASCII);
	private static final int MAX_PORTS = 4;

	private ArtPollPackets() {
	}

	static byte[] poll() {
		var packet = new byte[14];
		System.arraycopy(ID, 0, packet, 0, ID.length);
		packet[8] = (byte) (OP_POLL & 0xFF);
		packet[9] = (byte) (OP_POLL >> 8);
		packet[10] = 0;
		packet[11] = 14; // protocol version
		packet[12] = 0x02; // flags: send ArtPollReply whenever the node's state changes
		packet[13] = 0; // diagnostics priority
		return packet;
	}

	static int opCode(byte[] data, int length) {
		if (length < 10) {
			return -1;
		}
		for (int i = 0; i < ID.length; i++) {
			if (data[i] != ID[i]) {
				return -1;
			}
		}
		return Byte.toUnsignedInt(data[8]) | Byte.toUnsignedInt(data[9]) << 8;
	}

	/**
	 * @param sender address the packet came from, used if the reply does not contain an IP address
	 */
	static Optional<ArtNetNode> parseReply(byte[] data, int length, InetAddress sender, Instant now) {
		if (opCode(data, length) != OP_POLL_REPLY || length < MIN_REPLY_LENGTH) {
			return Optional.empty();
		}
		var ip = u(data, 10) + "." + u(data, 11) + "." + u(data, 12) + "." + u(data, 13);
		if (ip.equals("0.0.0.0")) {
			ip = sender.getHostAddress();
		}
		var net = u(data, 18) & 0x7F;
		var subnet = u(data, 19) & 0x0F;
		var numPorts = Math.min(MAX_PORTS, (u(data, 172) << 8 | u(data, 173)));
		var portAddresses = new ArrayList<Integer>();
		for (int i = 0; i < numPorts; i++) {
			var canOutputDmx = (u(data, 174 + i) & 0x80) != 0;
			if (canOutputDmx) {
				portAddresses.add(net << 8 | subnet << 4 | (u(data, 190 + i) & 0x0F));
			}
		}
		var mac = HexFormat.ofDelimiter(":").formatHex(data, 201, 207);
		var bindIndex = length > 211 ? u(data, 211) : 0;
		return Optional.of(new ArtNetNode(ip, bindIndex, text(data, 26, 18), text(data, 44, 64), mac,
				portAddresses, text(data, 108, 64), now));
	}

	private static int u(byte[] data, int index) {
		return Byte.toUnsignedInt(data[index]);
	}

	/** Null-terminated ASCII text of a fixed-size field. */
	private static String text(byte[] data, int offset, int size) {
		var end = offset;
		while (end < offset + size && data[end] != 0) {
			end++;
		}
		return new String(data, offset, end - offset, StandardCharsets.US_ASCII).strip();
	}
}
