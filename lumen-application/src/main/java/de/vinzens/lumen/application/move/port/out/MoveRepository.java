package de.vinzens.lumen.application.move.port.out;

import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveSpec;

import java.util.List;
import java.util.Optional;

public interface MoveRepository {

	/** Moves of the project, by name. */
	List<Move> findByProject(long projectId);

	Optional<Move> findById(long id);

	Move create(long projectId, MoveSpec spec);

	/** Replaces all settings and the fixtures. */
	Move update(long id, MoveSpec spec);

	void delete(long id);
}
