package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CompositionService implements ManageCompositionsUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final CompositionRepository compositions;
	private final StateEvents events;

	public CompositionService(ProjectRepository projects, SceneRepository scenes, FixtureRepository fixtures,
			GroupRepository groups, CompositionRepository compositions, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Composition> library(long projectId) {
		requireProject(projectId);
		return compositions.findLibrary(projectId);
	}

	@Override
	public Composition composition(long projectId, long compositionId) {
		return compositions.findById(compositionId)
				.filter(c -> c.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Composition " + compositionId + " does not exist in project "
						+ projectId));
	}

	@Override
	public Composition createInLibrary(long projectId, CompositionSpec spec) {
		requireProject(projectId);
		requireReferences(projectId, spec);
		return changed(compositions.create(projectId, Optional.empty(), Optional.empty(), spec));
	}

	@Override
	public Composition update(long projectId, long compositionId, CompositionSpec spec) {
		composition(projectId, compositionId);
		requireReferences(projectId, spec);
		return changed(compositions.update(compositionId, spec));
	}

	@Override
	public void delete(long projectId, long compositionId) {
		var existing = composition(projectId, compositionId);
		compositions.delete(compositionId);
		changed(existing);
	}

	@Override
	public Map<Long, List<Composition>> sceneCompositions(long projectId) {
		requireProject(projectId);
		return compositions.findByScenes(projectId);
	}

	@Override
	public List<Composition> sceneCompositions(long projectId, long sceneId) {
		requireScene(projectId, sceneId);
		return compositions.findByScene(sceneId);
	}

	@Override
	public Composition insertIntoScene(long projectId, long sceneId, long libraryCompositionId) {
		requireScene(projectId, sceneId);
		var source = requireLibrary(projectId, libraryCompositionId);
		return changed(compositions.create(projectId, Optional.of(sceneId), Optional.of(source.id()), source.spec()));
	}

	@Override
	public List<Composition> reorderScene(long projectId, long sceneId, List<Long> compositionIds) {
		requireScene(projectId, sceneId);
		var existing = compositions.findByScene(sceneId).stream().map(Composition::id).collect(Collectors.toSet());
		if (!existing.equals(new HashSet<>(compositionIds)) || existing.size() != compositionIds.size()) {
			throw new DomainValidationException(List.of("The order must contain every composition of the scene once"));
		}
		compositions.reorder(sceneId, compositionIds);
		events.publish(StateTopic.SCENES);
		events.publish(StateTopic.COMPOSITIONS);
		return compositions.findByScene(sceneId);
	}

	@Override
	public Composition reloadFromLibrary(long projectId, long copyId) {
		var copy = requireCopy(projectId, copyId);
		var source = copy.sourceId().flatMap(compositions::findById)
				.orElseThrow(() -> new ConflictException("The library composition of this copy no longer exists"));
		var spec = source.spec();
		return changed(compositions.update(copyId, copy.spec().withContent(spec.name(), spec.pattern(),
				spec.target())));
	}

	@Override
	public Composition saveToLibrary(long projectId, long copyId) {
		var copy = requireCopy(projectId, copyId);
		var spec = copy.spec();
		var source = copy.sourceId().flatMap(compositions::findById);
		if (source.isPresent()) {
			return changed(compositions.update(source.get().id(), source.get().spec().withContent(spec.name(),
					spec.pattern(), spec.target())));
		}
		return changed(compositions.create(projectId, Optional.empty(), Optional.empty(),
				CompositionSpec.of(spec.name(), spec.pattern(), spec.target())));
	}

	// --- checks ----------------------------------------------------------------------------------------------

	/** Groups and fixtures of target and steps must belong to the project. */
	private void requireReferences(long projectId, CompositionSpec spec) {
		var projectFixtures = fixtures.findByProject(projectId).stream().map(PatchedFixture::id)
				.collect(Collectors.toSet());
		var errors = new ArrayList<String>();
		switch (spec.target()) {
			case Target.Group group -> {
				if (groups.findById(group.groupId()).filter(g -> g.projectId() == projectId).isEmpty()) {
					errors.add("Group " + group.groupId() + " does not exist in this project");
				}
			}
			case Target.Fixtures list -> unknown(list.fixtureIds(), projectFixtures)
					.forEach(id -> errors.add("Fixture " + id + " does not exist in this project"));
			case Target.Steps steps -> spec.pattern().ownFixtures().ifPresent(ids -> unknown(ids, projectFixtures)
					.forEach(id -> errors.add("A step refers to fixture " + id + ", which is not in this project")));
		}
		if (!errors.isEmpty()) {
			throw new DomainValidationException(errors);
		}
	}

	private static List<Long> unknown(List<Long> ids, Set<Long> known) {
		return ids.stream().filter(id -> !known.contains(id)).toList();
	}

	private void requireProject(long projectId) {
		projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}

	private void requireScene(long projectId, long sceneId) {
		scenes.findById(sceneId).filter(s -> s.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + sceneId + " does not exist in project " + projectId));
	}

	private Composition requireLibrary(long projectId, long compositionId) {
		var composition = composition(projectId, compositionId);
		if (!composition.inLibrary()) {
			throw new DomainValidationException(List.of("Composition " + compositionId + " is not in the library"));
		}
		return composition;
	}

	private Composition requireCopy(long projectId, long compositionId) {
		var composition = composition(projectId, compositionId);
		if (composition.inLibrary()) {
			throw new DomainValidationException(List.of("Composition " + compositionId + " is not in a scene"));
		}
		return composition;
	}

	/** Copies in scenes change what the playback plays; library changes only the editor. */
	private Composition changed(Composition composition) {
		if (!composition.inLibrary()) {
			events.publish(StateTopic.SCENES);
		}
		events.publish(StateTopic.COMPOSITIONS);
		return composition;
	}
}
