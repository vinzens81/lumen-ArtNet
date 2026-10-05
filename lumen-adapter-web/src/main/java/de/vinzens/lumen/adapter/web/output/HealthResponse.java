package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase.OutputHealth;
import de.vinzens.lumen.application.output.port.in.WatchNodesUseCase.NodeCheck;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Health of the light output, measured over the last second.
 *
 * @param level ok, warning or error
 * @param nodes the nodes the project expects, empty while Art-Net is off
 */
public record HealthResponse(String level, List<String> problems, double renderRateHz, int targetRateHz,
		long renderErrors, String lastRenderError, int watchdogRestarts, boolean artNetActive,
		double packetsPerSecond, double sendErrorsPerSecond, int universes, List<NodeHealth> nodes) {

	public static HealthResponse from(OutputHealth health) {
		return new HealthResponse(health.level().name().toLowerCase(Locale.ROOT), health.problems(),
				health.renderRateHz(), health.targetRateHz(), health.renderErrors(),
				health.lastRenderError().orElse(null), health.watchdogRestarts(), health.artNetActive(),
				health.packetsPerSecond(), health.sendErrorsPerSecond(), health.universes(),
				health.nodes().stream().map(NodeHealth::from).toList());
	}

	/**
	 * @param state     online, offline or unchecked (a manual address that never answered ArtPoll)
	 * @param universes numbers of the project's universes the node receives
	 */
	public record NodeHealth(String ipAddress, String name, String state, List<Integer> universes, Instant lastSeen) {

		static NodeHealth from(NodeCheck check) {
			return new NodeHealth(check.ipAddress(), check.name(), check.state().name().toLowerCase(Locale.ROOT),
					check.universes(), check.lastSeen().orElse(null));
		}
	}
}
