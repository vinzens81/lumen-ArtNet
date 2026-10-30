package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Fade times of the change into a part of a scene, when the script passes its light marker.
 *
 * @param fadeIn  how long the light of the part takes to fade in, empty = the scene's fade-in
 * @param fadeOut how long the light before the marker takes to fade out, empty = the scene's fade-out
 */
public record SectionTiming(Optional<Duration> fadeIn, Optional<Duration> fadeOut) {

	/** The fade times of the scene. */
	public static final SectionTiming NONE = new SectionTiming(Optional.empty(), Optional.empty());

	public SectionTiming {
		Objects.requireNonNull(fadeIn, "fadeIn");
		Objects.requireNonNull(fadeOut, "fadeOut");
		new Validation()
				.require(fadeIn.map(SectionTiming::inRange).orElse(true), outOfRange("fadeIn"))
				.require(fadeOut.map(SectionTiming::inRange).orElse(true), outOfRange("fadeOut"))
				.throwIfInvalid();
	}

	public boolean isNone() {
		return equals(NONE);
	}

	public Duration fadeInOr(Duration sceneFadeIn) {
		return fadeIn.orElse(sceneFadeIn);
	}

	public Duration fadeOutOr(Duration sceneFadeOut) {
		return fadeOut.orElse(sceneFadeOut);
	}

	private static boolean inRange(Duration duration) {
		return !duration.isNegative() && duration.compareTo(SceneTiming.MAX) <= 0;
	}

	private static String outOfRange(String field) {
		return field + " must be between 0 and " + SceneTiming.MAX.toMinutes() + " minutes";
	}
}
