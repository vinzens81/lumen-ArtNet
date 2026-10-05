package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.Optional;

/**
 * JSON form of the fade times of a scene in milliseconds: {@code fadeInMs} and {@code fadeOutMs} are {@code null}
 * for the project's times, a missing {@code delayMs} is 0.
 */
public final class TimingJson {

	private TimingJson() {
	}

	public static SceneTiming fromJson(Long fadeInMs, Long fadeOutMs, Long delayMs) {
		return new SceneTiming(Optional.ofNullable(fadeInMs).map(Duration::ofMillis),
				Optional.ofNullable(fadeOutMs).map(Duration::ofMillis),
				Duration.ofMillis(delayMs == null ? 0 : delayMs));
	}

	public static Long fadeInMs(SceneTiming timing) {
		return timing.fadeIn().map(Duration::toMillis).orElse(null);
	}

	public static Long fadeOutMs(SceneTiming timing) {
		return timing.fadeOut().map(Duration::toMillis).orElse(null);
	}
}
