package de.vinzens.lumen.domain.project;

import java.util.Objects;

/**
 * Ein konkretes Gerät im Projekt: Gerätetyp aus der Bibliothek plus Universe, Adresse und Position.
 */
public record PatchedFixture(long id, long projectId, FixtureSpec spec) {

	public PatchedFixture {
		Objects.requireNonNull(spec, "spec");
	}
}
