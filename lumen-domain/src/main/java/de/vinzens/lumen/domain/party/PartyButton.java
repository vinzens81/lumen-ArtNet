package de.vinzens.lumen.domain.party;

import de.vinzens.lumen.domain.Validation;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;
import java.util.Objects;

/**
 * A button of party mode with its own values, e.g. a gobo or prism on the moving heads. Recorded from the
 * programmer in setup mode.
 *
 * @param position order in the button grid
 * @param values   the values the button sets while it is on, per fixture id
 */
public record PartyButton(long id, long projectId, int position, String name, KeyMode mode,
		Map<Long, FixtureValues> values) {

	public static final int MAX_PER_PROJECT = 24;

	public PartyButton {
		Objects.requireNonNull(mode, "mode");
		new Validation().requireText(name, "name").throwIfInvalid();
		name = name.strip();
		values = Map.copyOf(values);
	}
}
