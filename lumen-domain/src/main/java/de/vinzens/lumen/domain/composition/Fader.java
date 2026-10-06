package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * A fader of the show: plays a composition at the level it is pushed to, over the cue list – e.g. the house lights or
 * a work light, independent of GO.
 *
 * @param slot          1–{@value #MAX_SLOTS}, position in the fader bar
 * @param name          shown on the fader, empty = the composition's name
 * @param compositionId the composition it plays
 */
public record Fader(int slot, Optional<String> name, long compositionId) {

	public static final int MAX_SLOTS = 8;

	public Fader {
		Objects.requireNonNull(name, "name");
		new Validation().requireRange(slot, 1, MAX_SLOTS, "slot").throwIfInvalid();
		name = name.map(String::strip).filter(n -> !n.isEmpty());
	}
}
