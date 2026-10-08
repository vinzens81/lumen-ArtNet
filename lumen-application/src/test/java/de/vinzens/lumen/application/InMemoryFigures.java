package de.vinzens.lumen.application;

import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigureSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Figures in memory, for tests. */
public class InMemoryFigures implements FigureRepository {

	private final Map<Long, Figure> figures = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public List<Figure> findByProject(long projectId) {
		return figures.values().stream().filter(f -> f.projectId() == projectId).toList();
	}

	@Override
	public Optional<Figure> findById(long id) {
		return Optional.ofNullable(figures.get(id));
	}

	@Override
	public Figure create(long projectId, FigureSpec spec) {
		var figure = new Figure(nextId++, projectId, spec);
		figures.put(figure.id(), figure);
		return figure;
	}

	@Override
	public Figure update(long id, FigureSpec spec) {
		var figure = new Figure(id, figures.get(id).projectId(), spec);
		figures.put(id, figure);
		return figure;
	}

	@Override
	public void delete(long id) {
		figures.remove(id);
	}
}
