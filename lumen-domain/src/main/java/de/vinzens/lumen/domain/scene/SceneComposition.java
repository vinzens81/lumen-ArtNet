package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

/**
 * A composition used in a scene, with the settings of this use.
 *
 * @param level       how strongly the composition shows in this scene (0–1)
 * @param speedFactor multiplies the tempo (2 = twice as fast)
 * @param restart     start from the beginning at GO, even if the composition already runs in the scene before
 */
public record SceneComposition(long compositionId, double level, double speedFactor, boolean restart) {

	public SceneComposition {
		new Validation()
				.require(level >= 0 && level <= 1, "level must be between 0 and 1")
				.require(speedFactor >= 0.05 && speedFactor <= 20, "speedFactor must be between 0.05 and 20")
				.throwIfInvalid();
	}

	/** Full level, normal speed, continuing. */
	public static SceneComposition of(long compositionId) {
		return new SceneComposition(compositionId, 1, 1, false);
	}
}
