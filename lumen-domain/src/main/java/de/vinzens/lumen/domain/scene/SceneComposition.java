package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A composition used in a scene, with the settings of this use.
 *
 * @param level       how strongly the composition shows in this scene (0–1)
 * @param speedFactor multiplies the tempo (2 = twice as fast)
 * @param restart     start from the beginning at GO, even if the composition already runs in the scene before
 * @param fadeIn      how long it fades in at GO (after the delay of the scene), empty = the scene's fade-in
 * @param fadeOut     how long it fades out when it leaves the stage, empty = the fade-out of that transition
 */
public record SceneComposition(long compositionId, double level, double speedFactor, boolean restart,
		Optional<Duration> fadeIn, Optional<Duration> fadeOut) {

	public SceneComposition {
		Objects.requireNonNull(fadeIn, "fadeIn");
		Objects.requireNonNull(fadeOut, "fadeOut");
		new Validation()
				.require(level >= 0 && level <= 1, "level must be between 0 and 1")
				.require(speedFactor >= 0.05 && speedFactor <= 20, "speedFactor must be between 0.05 and 20")
				.require(fadeIn.map(SceneComposition::inRange).orElse(true), outOfRange("fadeIn"))
				.require(fadeOut.map(SceneComposition::inRange).orElse(true), outOfRange("fadeOut"))
				.throwIfInvalid();
	}

	/** A use with the fade times of the scene. */
	public SceneComposition(long compositionId, double level, double speedFactor, boolean restart) {
		this(compositionId, level, speedFactor, restart, Optional.empty(), Optional.empty());
	}

	/** Full level, normal speed, continuing. */
	public static SceneComposition of(long compositionId) {
		return new SceneComposition(compositionId, 1, 1, false);
	}

	/** The same use of another composition, e.g. after copying the composition into another project. */
	public SceneComposition withCompositionId(long newCompositionId) {
		return new SceneComposition(newCompositionId, level, speedFactor, restart, fadeIn, fadeOut);
	}

	private static boolean inRange(Duration duration) {
		return !duration.isNegative() && duration.compareTo(SceneTiming.MAX) <= 0;
	}

	private static String outOfRange(String field) {
		return field + " must be between 0 and " + SceneTiming.MAX.toMinutes() + " minutes";
	}
}
