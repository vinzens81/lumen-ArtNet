package de.vinzens.lumen.application.library.port.out;

import de.vinzens.lumen.domain.library.FixtureType;

import java.util.Objects;

/**
 * Ein geladener Gerätetyp mit seiner Herkunft (z.B. Dateiname) für verständliche Fehlermeldungen.
 */
public record SourcedFixtureType(FixtureType fixtureType, String source) {

	public SourcedFixtureType {
		Objects.requireNonNull(fixtureType, "fixtureType");
		Objects.requireNonNull(source, "source");
	}
}
