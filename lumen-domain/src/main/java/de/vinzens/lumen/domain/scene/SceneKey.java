package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * An extra key of a scene: switches a composition or a move on and off over the scene while it is on stage.
 *
 * @param slot          key 1–{@value #SLOTS}
 * @param compositionId the composition it plays; exactly one of composition and move
 * @param moveId        the move it plays
 * @param fade          time to fade in and out (0 = at once)
 */
public record SceneKey(int slot, Optional<Long> compositionId, Optional<Long> moveId, KeyMode mode,
		Duration fade) {

	public static final int SLOTS = 8;
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public SceneKey {
		Objects.requireNonNull(compositionId, "compositionId");
		Objects.requireNonNull(moveId, "moveId");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(fade, "fade");
		new Validation()
				.require(slot >= 1 && slot <= SLOTS, "slot must be between 1 and " + SLOTS + ", was " + slot)
				.require(!fade.isNegative() && fade.compareTo(MAX_FADE) <= 0, "fade must be between 0 and 10 minutes")
				.require(compositionId.isPresent() != moveId.isPresent(),
						"key " + slot + " plays either a composition or a move")
				.throwIfInvalid();
	}

	/** A key playing a composition. */
	public SceneKey(int slot, long compositionId, KeyMode mode, Duration fade) {
		this(slot, Optional.of(compositionId), Optional.empty(), mode, fade);
	}

	/** A key playing a move. */
	public static SceneKey move(int slot, long moveId, KeyMode mode, Duration fade) {
		return new SceneKey(slot, Optional.empty(), Optional.of(moveId), mode, fade);
	}

	/** Whether the key plays the composition. */
	public boolean plays(long composition) {
		return compositionId.equals(Optional.of(composition));
	}
}
