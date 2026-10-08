package de.vinzens.lumen.application.move.port.out;

import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigureSpec;

import java.util.List;
import java.util.Optional;

public interface FigureRepository {

	/** Figures of the project, by name. */
	List<Figure> findByProject(long projectId);

	Optional<Figure> findById(long id);

	Figure create(long projectId, FigureSpec spec);

	/** Replaces name, points and whether the figure is closed. */
	Figure update(long id, FigureSpec spec);

	void delete(long id);
}
