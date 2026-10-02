package de.vinzens.lumen.application.library.port.in;

import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;

import java.util.List;
import java.util.Optional;

/**
 * Lesender Zugriff auf die geladene Gerätebibliothek.
 */
public interface QueryFixtureLibraryUseCase {

	/** Alle gültig geladenen Gerätetypen, sortiert nach Hersteller und Modell. */
	List<FixtureType> fixtureTypes();

	Optional<FixtureType> fixtureType(String id);

	/** Probleme des letzten Ladevorgangs. */
	List<LibraryIssue> issues();
}
