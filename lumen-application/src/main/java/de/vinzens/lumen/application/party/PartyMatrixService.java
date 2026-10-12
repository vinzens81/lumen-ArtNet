package de.vinzens.lumen.application.party;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.party.port.in.ManagePartyMatrixUseCase;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.party.PartyMatrix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class PartyMatrixService implements ManagePartyMatrixUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final CompositionRepository compositions;
	private final MoveRepository moves;
	private final FixtureRepository fixtures;
	private final PartyMatrixRepository matrices;
	private final StateEvents events;

	public PartyMatrixService(ProjectRepository projects, SceneRepository scenes, CompositionRepository compositions,
			MoveRepository moves, FixtureRepository fixtures, PartyMatrixRepository matrices, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.matrices = Objects.requireNonNull(matrices, "matrices");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public PartyMatrix matrix(long projectId) {
		requireProject(projectId);
		return matrices.find(projectId);
	}

	@Override
	public PartyMatrix save(long projectId, PartyMatrix matrix) {
		requireProject(projectId);
		var sceneIds = new HashSet<Long>();
		scenes.findByProject(projectId).forEach(scene -> sceneIds.add(scene.id()));
		var compositionIds = new HashSet<Long>();
		compositions.findByProject(projectId).forEach(composition -> compositionIds.add(composition.id()));
		var moveIds = new HashSet<Long>();
		moves.findByProject(projectId).forEach(move -> moveIds.add(move.id()));
		var fixtureIds = new HashSet<Long>();
		fixtures.findByProject(projectId).forEach(fixture -> fixtureIds.add(fixture.id()));
		var errors = new ArrayList<String>();
		for (var cell : matrix.cells()) {
			var field = "Field " + (cell.row() + 1) + "/" + (cell.column() + 1);
			cell.sceneId().filter(id -> !sceneIds.contains(id))
					.ifPresent(id -> errors.add(field + " plays unknown scene " + id));
			cell.compositionId().filter(id -> !compositionIds.contains(id))
					.ifPresent(id -> errors.add(field + " plays unknown composition " + id));
			cell.moveId().filter(id -> !moveIds.contains(id))
					.ifPresent(id -> errors.add(field + " plays unknown move " + id));
			cell.fader().ifPresent(fader -> fader.fixtureIds().stream().filter(id -> !fixtureIds.contains(id))
					.forEach(id -> errors.add(field + " sets unknown fixture " + id)));
		}
		if (!errors.isEmpty()) {
			throw new DomainValidationException(List.copyOf(errors));
		}
		matrices.save(projectId, matrix);
		events.publish(StateTopic.PARTY_MATRIX);
		return matrices.find(projectId);
	}

	private void requireProject(long projectId) {
		projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}
}
