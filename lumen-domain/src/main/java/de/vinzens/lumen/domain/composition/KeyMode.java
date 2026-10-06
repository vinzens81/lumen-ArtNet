package de.vinzens.lumen.domain.composition;

import de.vinzens.lumen.domain.DomainValidationException;

import java.util.List;
import java.util.Locale;

/** How an extra key in show mode behaves. */
public enum KeyMode {
	/** Press switches on, the next press switches off. */
	TOGGLE,
	/** On while the key is held down. */
	FLASH;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static KeyMode parse(String id) {
		for (var mode : values()) {
			if (mode.id().equals(id)) {
				return mode;
			}
		}
		throw new DomainValidationException(List.of("mode must be toggle or flash, was '" + id + "'"));
	}
}
