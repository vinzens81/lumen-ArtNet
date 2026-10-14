package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.Universe;

import java.util.List;

/**
 * Hands every frame to several outputs, e.g. Art-Net and sACN; each one sends only the universes of its protocol. The
 * counters are the sums, active if any output is. Closing it closes the outputs.
 */
public final class CombinedDmxOutput implements DmxOutputPort, AutoCloseable {

	private final List<DmxOutputPort> outputs;

	private CombinedDmxOutput(List<DmxOutputPort> outputs) {
		this.outputs = outputs;
	}

	/** {@link DmxOutputPort#NONE} for none, the output itself for one. */
	public static DmxOutputPort of(List<DmxOutputPort> outputs) {
		var active = outputs.stream().filter(output -> output != NONE).toList();
		return switch (active.size()) {
			case 0 -> NONE;
			case 1 -> active.getFirst();
			default -> new CombinedDmxOutput(active);
		};
	}

	@Override
	public void send(Universe universe, DmxFrame frame) {
		outputs.forEach(output -> output.send(universe, frame));
	}

	@Override
	public Counters counters() {
		var counters = outputs.stream().map(DmxOutputPort::counters).toList();
		return new Counters(counters.stream().anyMatch(Counters::active),
				counters.stream().mapToLong(Counters::packetsSent).sum(),
				counters.stream().mapToLong(Counters::sendErrors).sum());
	}

	@Override
	public void close() throws Exception {
		for (var output : outputs) {
			if (output instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}
}
