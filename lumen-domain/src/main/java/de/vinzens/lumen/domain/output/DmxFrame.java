package de.vinzens.lumen.domain.output;

import java.util.Arrays;

/**
 * The 512 channel values of one DMX universe. Immutable.
 */
public final class DmxFrame {

	public static final int SIZE = 512;
	public static final DmxFrame BLACK = new DmxFrame(new byte[SIZE]);

	private final byte[] data;

	private DmxFrame(byte[] data) {
		this.data = data;
	}

	/** Value of a channel (1–512) as 0–255. */
	public int get(int channel) {
		return Byte.toUnsignedInt(data[channel - 1]);
	}

	/** Copy of the raw data, index 0 = channel 1. */
	public byte[] toByteArray() {
		return data.clone();
	}

	public int[] toIntArray() {
		var values = new int[SIZE];
		for (int i = 0; i < SIZE; i++) {
			values[i] = Byte.toUnsignedInt(data[i]);
		}
		return values;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DmxFrame frame && Arrays.equals(data, frame.data);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}

	public static final class Builder {

		private final byte[] data = new byte[SIZE];

		/** Sets a channel (1–512); values outside the universe are ignored. */
		public Builder set(int channel, int value) {
			if (channel >= 1 && channel <= SIZE) {
				data[channel - 1] = (byte) Math.clamp(value, 0, 255);
			}
			return this;
		}

		public DmxFrame build() {
			return new DmxFrame(data.clone());
		}
	}
}
