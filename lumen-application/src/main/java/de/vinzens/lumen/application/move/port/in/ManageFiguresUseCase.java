package de.vinzens.lumen.application.move.port.in;

import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigureSpec;

import java.util.List;

/**
 * Figures of a project: shapes in the pan/tilt grid, drawn without fixtures. Moves lay them onto fixtures; a change
 * applies to every move using the figure.
 */
public interface ManageFiguresUseCase {

	/** By name. */
	List<Figure> figures(long projectId);

	Figure figure(long projectId, long figureId);

	Figure create(long projectId, FigureSpec spec);

	Figure update(long projectId, long figureId, FigureSpec spec);

	/** A copy, used by no move. */
	Figure duplicate(long projectId, long figureId);

	/** Deletes a figure no move uses; otherwise a conflict names the moves. */
	void delete(long projectId, long figureId);
}
