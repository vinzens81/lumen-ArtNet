package de.vinzens.lumen.adapter.web.scene;

import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.Optional;

/**
 * JSON form of the fade times of a scene in milliseconds: {@code fadeInMs} and {@code fadeOutMs} are {@code null}
 * for the project's times, a missing {@code delayMs} is 0. {@code fadeCurve} is a {@link FadeCurve} id or
 * {@code null} for the project's curve.
 */
public final class TimingJson {

	private TimingJson() {
	}

	public static SceneTiming fromJson(Long fadeInMs, Long fadeOutMs, Long delayMs, String fadeCurve) {
		return new SceneTiming(Optional.ofNullable(fadeInMs).map(Duration::ofMillis),
				Optional.ofNullable(fadeOutMs).map(Duration::ofMillis),
				Duration.ofMillis(delayMs == null ? 0 : delayMs),
				Optional.ofNullable(fadeCurve).filter(c -> !c.isBlank()).map(FadeCurve::parse));
	}

	public static String fadeCurve(SceneTiming timing) {
		return timing.curve().map(FadeCurve::id).orElse(null);
	}

	public static Long fadeInMs(SceneTiming timing) {
		return timing.fadeIn().map(Duration::toMillis).orElse(null);
	}

	public static Long fadeOutMs(SceneTiming timing) {
		return timing.fadeOut().map(Duration::toMillis).orElse(null);
	}
}
