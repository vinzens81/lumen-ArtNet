package de.vinzens.lumen.application.composition.port.out;

import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;

import java.util.List;
import java.util.Optional;

public interface CompositionRepository {

	/** Compositions of the project, by name. */
	List<Composition> findByProject(long projectId);

	Optional<Composition> findById(long id);

	Composition create(long projectId, CompositionSpec spec, List<CompositionStep> steps);

	/** Replaces properties and steps. */
	Composition update(long id, CompositionSpec spec, List<CompositionStep> steps);

	/** Deletes the composition with its own looks. */
	void delete(long id);
}
