package de.vinzens.lumen.application.output.port.in;

import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeCheck;

import java.util.List;
import java.util.Optional;

/**
 * Whether the light output is working: render loop, Art-Net and the nodes of the project, measured over the last
 * second.
 */
public interface QueryHealthUseCase {

	OutputHealth health();

	enum Level {
		OK, WARNING, ERROR
	}

	/**
	 * @param level              worst of all problems
	 * @param problems           readable descriptions, empty when everything works
	 * @param renderRateHz       measured ticks per second
	 * @param targetRateHz       configured ticks per second
	 * @param renderErrors       failed ticks since start
	 * @param lastRenderError    message of the last failed tick
	 * @param watchdogRestarts   how often a stalled render loop was restarted
	 * @param artNetActive       whether packets are sent into the network at all
	 * @param packetsPerSecond   Art-Net packets sent per second
	 * @param sendErrorsPerSecond packets per second that could not be sent
	 * @param universes          universes of the active project
	 * @param nodes              the nodes the project expects, empty while Art-Net is off
	 */
	record OutputHealth(Level level, List<String> problems, double renderRateHz, int targetRateHz, long renderErrors,
			Optional<String> lastRenderError, int watchdogRestarts, boolean artNetActive, double packetsPerSecond,
			double sendErrorsPerSecond, int universes, List<NodeCheck> nodes) {

		public static final OutputHealth UNKNOWN = new OutputHealth(Level.OK, List.of(), 0, 0, 0, Optional.empty(), 0,
				false, 0, 0, 0, List.of());

		public OutputHealth {
			problems = List.copyOf(problems);
			nodes = List.copyOf(nodes);
		}
	}
}
