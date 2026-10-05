package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Fade times of the GO into a scene, e.g. 0 s for a flash. They apply to the whole transition into the scene: the
 * fade-in of the scene and the fade-out of what was on stage before.
 *
 * @param fadeIn  overrides the project's fade-in time, empty = the project's
 * @param fadeOut overrides the project's fade-out time for the look on stage at GO, empty = the project's
 * @param delay   the fade-in starts this long after GO, while the fade-out starts at once
 */
public record SceneTiming(Optional<Duration> fadeIn, Optional<Duration> fadeOut, Duration delay) {

	public static final Duration MAX = Duration.ofMinutes(10);
	public static final SceneTiming DEFAULT = new SceneTiming(Optional.empty(), Optional.empty(), Duration.ZERO);

	public SceneTiming {
		Objects.requireNonNull(fadeIn, "fadeIn");
		Objects.requireNonNull(fadeOut, "fadeOut");
		Objects.requireNonNull(delay, "delay");
		new Validation()
				.require(fadeIn.map(SceneTiming::inRange).orElse(true), outOfRange("fadeIn"))
				.require(fadeOut.map(SceneTiming::inRange).orElse(true), outOfRange("fadeOut"))
				.require(inRange(delay), outOfRange("delay"))
				.throwIfInvalid();
	}

	/** Whether the scene uses the project's times without a delay. */
	public boolean isDefault() {
		return equals(DEFAULT);
	}

	public Duration fadeInOr(Duration projectFadeIn) {
		return fadeIn.orElse(projectFadeIn);
	}

	public Duration fadeOutOr(Duration projectFadeOut) {
		return fadeOut.orElse(projectFadeOut);
	}

	private static boolean inRange(Duration duration) {
		return !duration.isNegative() && duration.compareTo(MAX) <= 0;
	}

	private static String outOfRange(String field) {
		return field + " must be between 0 and " + MAX.toMinutes() + " minutes";
	}
}
