package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;

/**
 * An extra key of show mode: switches its own copy of a composition on and off, independent of the scene.
 *
 * @param slot        key 1–8
 * @param fade        time to fade in and out (0 = at once)
 * @param composition the copy the key plays
 */
public record ExtraKey(int slot, KeyMode mode, Duration fade, Composition composition) {

	public static final int SLOTS = 8;
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	public ExtraKey {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(fade, "fade");
		Objects.requireNonNull(composition, "composition");
		validate(slot, fade);
	}

	public static void validate(int slot, Duration fade) {
		new Validation()
				.require(slot >= 1 && slot <= SLOTS, "slot must be between 1 and " + SLOTS + ", was " + slot)
				.require(!fade.isNegative() && fade.compareTo(MAX_FADE) <= 0, "fade must be between 0 and 10 minutes")
				.throwIfInvalid();
	}
}
