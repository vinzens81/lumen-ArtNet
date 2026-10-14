package de.vinzens.lumen.application.output;

import de.vinzens.lumen.application.output.port.out.DmxOutputPort;
import de.vinzens.lumen.application.output.port.out.DmxOutputPort.Counters;
import de.vinzens.lumen.domain.output.DmxFrame;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CombinedDmxOutputTest {

	private static final Universe UNIVERSE = new Universe(1, 1, new UniverseSpec(1, Optional.empty(),
			new ArtNetAddress(0, 0, 0), ArtNetTarget.auto()));

	@Test
	void sendsEveryFrameToEachOutputAndSumsTheCounters() {
		var a = new Recording(new Counters(true, 10, 1));
		var b = new Recording(new Counters(false, 5, 2));
		var combined = CombinedDmxOutput.of(List.of(a, b));

		combined.send(UNIVERSE, DmxFrame.BLACK);

		assertThat(a.sent).containsExactly(UNIVERSE);
		assertThat(b.sent).containsExactly(UNIVERSE);
		assertThat(combined.counters()).isEqualTo(new Counters(true, 15, 3));
	}

	@Test
	void leavesOutDisabledOutputs() {
		var a = new Recording(Counters.INACTIVE);

		assertThat(CombinedDmxOutput.of(List.of(DmxOutputPort.NONE, a))).isSameAs(a);
		assertThat(CombinedDmxOutput.of(List.of(DmxOutputPort.NONE))).isSameAs(DmxOutputPort.NONE);
	}

	@Test
	void closesTheOutputs() throws Exception {
		var a = new Recording(Counters.INACTIVE);
		var b = new Recording(Counters.INACTIVE);

		((AutoCloseable) CombinedDmxOutput.of(List.of(a, b))).close();

		assertThat(a.closed).isTrue();
		assertThat(b.closed).isTrue();
	}

	private static final class Recording implements DmxOutputPort, AutoCloseable {

		private final Counters counters;
		private final List<Universe> sent = new ArrayList<>();
		private boolean closed;

		Recording(Counters counters) {
			this.counters = counters;
		}

		@Override
		public void send(Universe universe, DmxFrame frame) {
			sent.add(universe);
		}

		@Override
		public Counters counters() {
			return counters;
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}
