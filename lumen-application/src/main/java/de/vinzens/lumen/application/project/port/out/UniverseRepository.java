package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;

import java.util.List;
import java.util.Optional;

public interface UniverseRepository {

	/** Universen des Projekts, sortiert nach Nummer. */
	List<Universe> findByProject(long projectId);

	Optional<Universe> findById(long id);

	Universe create(long projectId, UniverseSpec spec);

	Universe update(long id, UniverseSpec spec);

	void delete(long id);
}
