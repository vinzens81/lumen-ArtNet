package de.vinzens.lumen.application.library.port.out;

import de.vinzens.lumen.domain.library.FixtureType;

import java.util.Objects;

/**
 * A loaded fixture type with its origin (e.g. file name) for understandable error messages.
 */
public record SourcedFixtureType(FixtureType fixtureType, String source) {

	public SourcedFixtureType {
		Objects.requireNonNull(fixtureType, "fixtureType");
		Objects.requireNonNull(source, "source");
	}
}
