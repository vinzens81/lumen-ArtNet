package de.vinzens.lumen.application.library.port.out;

import de.vinzens.lumen.domain.library.LibraryIssue;

import java.util.List;

/**
 * Ergebnis eines Ladevorgangs der Gerätebibliothek.
 */
public record LoadResult(List<SourcedFixtureType> fixtureTypes, List<LibraryIssue> issues) {

	public LoadResult {
		fixtureTypes = List.copyOf(fixtureTypes);
		issues = List.copyOf(issues);
	}
}
