package de.vinzens.lumen.domain.composition;

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
}
