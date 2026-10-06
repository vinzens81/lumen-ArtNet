package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

/**
 * A look used directly in a scene (or a part of it), without a composition: it stays as it is while the scene is on
 * stage.
 *
 * @param level how strongly the look shows (0–1)
 */
public record SceneLook(long lookId, double level) {

	public SceneLook {
		new Validation().require(level >= 0 && level <= 1, "level must be between 0 and 1").throwIfInvalid();
	}

	public static SceneLook of(long lookId) {
		return new SceneLook(lookId, 1);
	}
}
