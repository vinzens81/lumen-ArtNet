package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * A fader of the show: plays a scene at the level it is pushed to, over the cue list – e.g. the house lights or a
 * work light, independent of GO.
 *
 * @param slot    1–{@value #MAX_SLOTS}, position in the fader bar
 * @param name    shown on the fader, empty = the scene's name
 * @param sceneId the scene it plays, usually one "only for extra keys"
 */
public record Fader(int slot, Optional<String> name, long sceneId) {

	public static final int MAX_SLOTS = 8;

	public Fader {
		Objects.requireNonNull(name, "name");
		new Validation().requireRange(slot, 1, MAX_SLOTS, "slot").throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
	}
}
