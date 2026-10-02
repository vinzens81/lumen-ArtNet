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
}
