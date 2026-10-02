package de.vinzens.lumen.domain.library;

import java.util.Arrays;
import java.util.Optional;

/**
 * Rough kind of fixture, used e.g. for icons in the UI.
 */
public enum FixtureCategory {
	PAR("par"),
	FRESNEL("fresnel"),
	PROFILE("profile"),
	MOVING_HEAD("moving-head"),
	SCANNER("scanner"),
	BAR("bar"),
	DIMMER("dimmer"),
	STROBE("strobe"),
	FOG("fog"),
	OTHER("other");

	private final String id;

	FixtureCategory(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static Optional<FixtureCategory> fromId(String id) {
		return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
	}
}
