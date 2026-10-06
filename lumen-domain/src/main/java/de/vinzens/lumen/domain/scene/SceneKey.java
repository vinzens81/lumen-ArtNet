package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.Objects;

/**
 * An extra key of a scene: switches a composition on and off over the scene while it is on stage.
 *
 * @param slot key 1–{@value #SLOTS}
 * @param fade time to fade in and out (0 = at once)
 */
public record SceneKey(int slot, long compositionId, KeyMode mode, Duration fade) {

	public static final int SLOTS = 8;
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public SceneKey {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(fade, "fade");
		new Validation()
				.require(slot >= 1 && slot <= SLOTS, "slot must be between 1 and " + SLOTS + ", was " + slot)
				.require(!fade.isNegative() && fade.compareTo(MAX_FADE) <= 0, "fade must be between 0 and 10 minutes")
				.throwIfInvalid();
	}
}
