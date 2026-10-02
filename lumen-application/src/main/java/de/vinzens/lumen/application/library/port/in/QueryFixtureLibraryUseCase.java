package de.vinzens.lumen.application.library.port.in;

import de.vinzens.lumen.domain.library.FixtureType;
import de.vinzens.lumen.domain.library.LibraryIssue;

import java.util.List;
import java.util.Optional;

/**
 * Read access to the loaded fixture library.
 */
public interface QueryFixtureLibraryUseCase {

	/** All successfully loaded fixture types, sorted by manufacturer and model. */
	List<FixtureType> fixtureTypes();

	Optional<FixtureType> fixtureType(String id);

	/** Issues of the last load. */
	List<LibraryIssue> issues();
}
