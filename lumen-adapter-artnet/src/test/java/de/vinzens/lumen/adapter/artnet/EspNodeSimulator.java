package de.vinzens.lumen.adapter.artnet;

import java.nio.charset.StandardCharsets;

/**
 * Builds ArtPollReply packets byte by byte like the firmware of the self-built ESP8266 dimmer
 * ({@code sendPollReply()} in its main.cpp), so parsing is tested against the real device format.
 */
final class EspNodeSimulator {

	private EspNodeSimulator() {
	}

	static byte[] pollReply(int[] ip, int universe, String name) {
		var r = new byte[239];
		System.arraycopy("Art-Net\0".getBytes(StandardCharsets.US_ASCII), 0, r, 0, 8);
		r[8] = 0x00;
		r[9] = 0x21;
		for (int i = 0; i < 4; i++) {
			r[10 + i] = (byte) ip[i];
		}
		r[14] = 0x36;
		r[15] = 0x19;
		r[18] = (byte) ((universe >> 8) & 0x7F);
		r[19] = (byte) ((universe >> 4) & 0x0F);
		r[23] = (byte) 0xE0;
		copy(name, r, 26, 17);
		copy(name + " (ESP8266 Art-Net Dimmer)", r, 44, 63);
		copy("#0001 [0000] DMX OK", r, 108, 63);
		r[173] = 0x01;
		r[174] = (byte) 0x80;
		r[182] = (byte) 0x80;
		r[190] = (byte) (universe & 0x0F);
		r[200] = 0x00;
		byte[] mac = {0x24, 0x0a, (byte) 0xc4, 0x12, 0x34, 0x56};
		System.arraycopy(mac, 0, r, 201, 6);
		r[212] = 0x08;
		return r;
	}

	private static void copy(String text, byte[] target, int offset, int max) {
		var bytes = text.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, target, offset, Math.min(bytes.length, max));
	}
}
