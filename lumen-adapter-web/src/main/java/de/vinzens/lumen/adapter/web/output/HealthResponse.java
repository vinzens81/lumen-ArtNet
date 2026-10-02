package de.vinzens.lumen.adapter.web.output;

import de.vinzens.lumen.application.output.port.in.QueryHealthUseCase.OutputHealth;

import java.util.List;
import java.util.Locale;

/**
 * Health of the light output, measured over the last second.
 *
 * @param level ok, warning or error
 */
public record HealthResponse(String level, List<String> problems, double renderRateHz, int targetRateHz,
		long renderErrors, String lastRenderError, int watchdogRestarts, boolean artNetActive,
		double packetsPerSecond, double sendErrorsPerSecond, int universes) {

	public static HealthResponse from(OutputHealth health) {
		return new HealthResponse(health.level().name().toLowerCase(Locale.ROOT), health.problems(),
				health.renderRateHz(), health.targetRateHz(), health.renderErrors(),
				health.lastRenderError().orElse(null), health.watchdogRestarts(), health.artNetActive(),
				health.packetsPerSecond(), health.sendErrorsPerSecond(), health.universes());
	}
}
