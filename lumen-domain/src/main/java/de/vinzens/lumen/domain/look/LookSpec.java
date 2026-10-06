package de.vinzens.lumen.domain.look;

import de.vinzens.lumen.domain.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Editable properties of a look.
 *
 * @param owner the composition the look belongs to (recorded as a step or made by a generator); empty = a shared
 *              look of the project that any composition can use
 */
public record LookSpec(String name, Optional<Long> owner) {

	public LookSpec {
		Objects.requireNonNull(owner, "owner");
		new Validation().requireText(name, "name").throwIfInvalid();
		name = name.strip();
	}

	/** A shared look. */
	public static LookSpec shared(String name) {
		return new LookSpec(name, Optional.empty());
	}

	public boolean isShared() {
		return owner.isEmpty();
	}
}
