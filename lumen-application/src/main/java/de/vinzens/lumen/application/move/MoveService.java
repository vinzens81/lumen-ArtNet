package de.vinzens.lumen.application.move;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.PatchAttributes;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.move.port.in.ManageMovesUseCase;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.MovePattern;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MoveService implements ManageMovesUseCase {

	private final ProjectRepository projects;
	private final MoveRepository moves;
	private final FigureRepository figures;
	private final FixtureRepository fixtures;
	private final SceneRepository scenes;
	private final PartyMatrixRepository partyMatrices;
	private final QueryFixtureLibraryUseCase library;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;

	public MoveService(ProjectRepository projects, MoveRepository moves, FigureRepository figures,
			FixtureRepository fixtures, SceneRepository scenes, PartyMatrixRepository partyMatrices,
			QueryFixtureLibraryUseCase library, ControlProgrammerUseCase programmer, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.figures = Objects.requireNonNull(figures, "figures");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.partyMatrices = Objects.requireNonNull(partyMatrices, "partyMatrices");
		this.library = Objects.requireNonNull(library, "library");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Move> moves(long projectId) {
		requireProject(projectId);
		return moves.findByProject(projectId);
	}

	@Override
	public Move move(long projectId, long moveId) {
		requireProject(projectId);
		return moves.findById(moveId)
				.filter(move -> move.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Move " + moveId + " does not exist in project " + projectId));
	}

	@Override
	public Move create(long projectId, MoveSpec spec) {
		requireProject(projectId);
		requireValid(projectId, spec);
		return published(moves.create(projectId, spec));
	}

	@Override
	public Move update(long projectId, long moveId, MoveSpec spec) {
		move(projectId, moveId);
		requireValid(projectId, spec);
		return published(moves.update(moveId, spec));
	}

	@Override
	public Move duplicate(long projectId, long moveId) {
		var original = move(projectId, moveId);
		return published(moves.create(projectId, original.spec().withName(original.spec().name() + " (copy)")));
	}

	@Override
	public void delete(long projectId, long moveId) {
		move(projectId, moveId);
		var users = new ArrayList<String>();
		scenes.findByProject(projectId).stream()
				.filter(scene -> scene.containsMove(moveId))
				.forEach(scene -> users.add("scene " + scene.spec().number()));
		partyMatrices.find(projectId).cells().stream()
				.filter(cell -> cell.moveId().equals(Optional.of(moveId)))
				.forEach(cell -> users.add("party field " + (cell.row() + 1) + "/" + (cell.column() + 1)));
		if (!users.isEmpty()) {
			throw new ConflictException("The move is played by " + String.join(", ", users)
					+ " – remove it from there first");
		}
		moves.delete(moveId);
		events.publish(StateTopic.MOVES);
	}

	@Override
	public Move recordPosition(long projectId, Optional<Long> moveId, String name, List<Long> fixtureIds) {
		var project = projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
		if (!project.active()) {
			throw new ConflictException("The programmer belongs to the active project – activate project "
					+ projectId + " first");
		}
		var existing = moveId.map(id -> move(projectId, id));
		var values = programmer.programmerValues();
		var positions = new LinkedHashMap<Long, FigurePoint>();
		for (var fixtureId : new LinkedHashSet<>(fixtureIds)) {
			var fixtureValues = values.get(fixtureId);
			var pan = fixtureValues == null ? Optional.<Double>empty() : fixtureValues.get(MovePattern.PAN);
			var tilt = fixtureValues == null ? Optional.<Double>empty() : fixtureValues.get(MovePattern.TILT);
			if (pan.isPresent() && tilt.isPresent()) {
				positions.put(fixtureId, new FigurePoint(pan.get(), tilt.get()));
			}
		}
		if (positions.isEmpty()) {
			throw new DomainValidationException(List.of("none of the fixtures has pan and tilt in the programmer"));
		}
		var middle = new FigurePoint(positions.values().stream().mapToDouble(FigurePoint::x).average().orElseThrow(),
				positions.values().stream().mapToDouble(FigurePoint::y).average().orElseThrow());
		var moveFixtures = new ArrayList<MoveFixture>();
		positions.forEach((fixtureId, position) -> moveFixtures.add(new MoveFixture(fixtureId,
				position.x() - middle.x(), position.y() - middle.y(), false, false, false)));
		var moveName = existing.map(m -> m.spec().name()).orElse(name);
		var figure = figureFor(projectId, existing, new FigureSpec(moveName, false, List.of(middle)));
		var spec = existing
				.map(m -> new MoveSpec(moveName, figure, moveFixtures, 1, 1, 0, 0, 0, m.spec().durationSeconds(),
						m.spec().repeat(), m.spec().stepOffset(), m.spec().onceStart()))
				.orElseGet(() -> MoveSpec.of(moveName, figure, moveFixtures));
		requireValid(projectId, spec);
		return published(existing.isPresent() ? moves.update(existing.get().id(), spec) : moves.create(projectId, spec));
	}

	/** The figure of the move if no other move uses it, otherwise a new one. */
	private long figureFor(long projectId, Optional<Move> move, FigureSpec point) {
		var own = move.map(m -> m.spec().figureId())
				.filter(figureId -> moves.findByProject(projectId).stream()
						.noneMatch(other -> other.id() != move.get().id() && other.spec().figureId() == figureId));
		return own.isPresent() ? figures.update(own.get(), point).id() : figures.create(projectId, point).id();
	}

	/** The figure must belong to the project, every fixture too and have pan and tilt. */
	private void requireValid(long projectId, MoveSpec spec) {
		var problems = new ArrayList<String>();
		if (figures.findById(spec.figureId()).filter(f -> f.projectId() == projectId).isEmpty()) {
			problems.add("figure " + spec.figureId() + " does not exist in this project");
		}
		var patched = fixtures.findByProject(projectId);
		var byId = patched.stream().collect(Collectors.toMap(PatchedFixture::id, Function.identity()));
		var attributes = PatchAttributes.of(patched, library);
		for (var fixtureId : spec.fixtureIds()) {
			var fixture = byId.get(fixtureId);
			if (fixture == null) {
				problems.add("fixture " + fixtureId + " does not exist in this project");
			} else if (!attributes.has(fixtureId, MovePattern.PAN) || !attributes.has(fixtureId, MovePattern.TILT)) {
				problems.add("\"" + fixture.spec().name() + "\" has no pan and tilt");
			}
		}
		if (!problems.isEmpty()) {
			throw new DomainValidationException(problems);
		}
	}

	private void requireProject(long projectId) {
		if (projects.findById(projectId).isEmpty()) {
			throw new NotFoundException("Project " + projectId + " does not exist");
		}
	}

	private Move published(Move move) {
		events.publish(StateTopic.MOVES);
		return move;
	}
}
