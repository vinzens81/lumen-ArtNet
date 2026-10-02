package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * An extra key of show mode: switches its own copy of a composition, or a whole scene, on and off over the
 * playback, independent of the scene on stage.
 *
 * @param slot key 1–8
 * @param fade time to fade in and out (0 = at once)
 */
public record ExtraKey(int slot, KeyMode mode, Duration fade, Content content) {

	public static final int SLOTS = 8;
	public static final Duration MAX_FADE = Duration.ofMinutes(10);

	/** What a key plays. */
	public sealed interface Content {

		/** The key's own copy of a composition. */
		record PlaysComposition(Composition composition) implements Content {

			public PlaysComposition {
				Objects.requireNonNull(composition, "composition");
			}
		}

		/** A scene of the project – its values and its compositions; changes to the scene apply at once. */
		record PlaysScene(long sceneId) implements Content {
		}
	}

	public ExtraKey {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(fade, "fade");
		Objects.requireNonNull(content, "content");
		validate(slot, fade);
	}

	public ExtraKey(int slot, KeyMode mode, Duration fade, Composition composition) {
		this(slot, mode, fade, new Content.PlaysComposition(composition));
	}

	/** The composition of the key, unless it plays a scene. */
	public Optional<Composition> composition() {
		return content instanceof Content.PlaysComposition plays ? Optional.of(plays.composition()) : Optional.empty();
	}

	/** The scene the key plays, unless it plays a composition. */
	public Optional<Long> sceneId() {
		return content instanceof Content.PlaysScene plays ? Optional.of(plays.sceneId()) : Optional.empty();
	}

	public static void validate(int slot, Duration fade) {
		new Validation()
				.require(slot >= 1 && slot <= SLOTS, "slot must be between 1 and " + SLOTS + ", was " + slot)
				.require(!fade.isNegative() && fade.compareTo(MAX_FADE) <= 0, "fade must be between 0 and 10 minutes")
				.throwIfInvalid();
	}
}
