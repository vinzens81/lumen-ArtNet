package de.vinzens.lumen.application.output.port.out;

import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.Universe;

/**
 * Sends DMX frames to the outside world, e.g. via Art-Net. Called from the render loop for every patched
 * universe on every tick; implementations must not throw.
 */
public interface DmxOutputPort {

	DmxOutputPort NONE = (universe, frame) -> {
	};

	void send(Universe universe, DmxFrame frame);

	/** Totals since start, for the health display. */
	default Counters counters() {
		return Counters.INACTIVE;
	}

	/**
	 * @param active      whether packets are sent into the network at all
	 * @param packetsSent packets handed to the network
	 * @param sendErrors  packets that could not be sent
	 */
	record Counters(boolean active, long packetsSent, long sendErrors) {

		public static final Counters INACTIVE = new Counters(false, 0, 0);
	}
}
