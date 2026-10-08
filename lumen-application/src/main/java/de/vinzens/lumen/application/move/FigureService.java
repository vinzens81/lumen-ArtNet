package de.vinzens.lumen.application.move;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.move.port.in.ManageFiguresUseCase;
import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigureSpec;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FigureService implements ManageFiguresUseCase {

	private final ProjectRepository projects;
	private final FigureRepository figures;
	private final MoveRepository moves;
	private final StateEvents events;

	public FigureService(ProjectRepository projects, FigureRepository figures, MoveRepository moves,
			StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.figures = Objects.requireNonNull(figures, "figures");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Figure> figures(long projectId) {
		requireProject(projectId);
		return figures.findByProject(projectId);
	}

	@Override
	public Figure figure(long projectId, long figureId) {
		requireProject(projectId);
		return figures.findById(figureId)
				.filter(figure -> figure.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Figure " + figureId + " does not exist in project "
						+ projectId));
	}

	@Override
	public Figure create(long projectId, FigureSpec spec) {
		requireProject(projectId);
		return published(figures.create(projectId, spec));
	}

	@Override
	public Figure update(long projectId, long figureId, FigureSpec spec) {
		figure(projectId, figureId);
		return published(figures.update(figureId, spec));
	}

	@Override
	public Figure duplicate(long projectId, long figureId) {
		var original = figure(projectId, figureId);
		return published(figures.create(projectId, original.spec().withName(original.spec().name() + " (copy)")));
	}

	@Override
	public void delete(long projectId, long figureId) {
		figure(projectId, figureId);
		var users = moves.findByProject(projectId).stream()
				.filter(move -> move.spec().figureId() == figureId)
				.map(move -> "\"" + move.spec().name() + "\"")
				.collect(Collectors.joining(", "));
		if (!users.isEmpty()) {
			throw new ConflictException("The figure is used by " + users + " – choose another figure there first");
		}
		figures.delete(figureId);
		events.publish(StateTopic.MOVES);
	}

	private void requireProject(long projectId) {
		if (projects.findById(projectId).isEmpty()) {
			throw new NotFoundException("Project " + projectId + " does not exist");
		}
	}

	private Figure published(Figure figure) {
		events.publish(StateTopic.MOVES);
		return figure;
	}
}
