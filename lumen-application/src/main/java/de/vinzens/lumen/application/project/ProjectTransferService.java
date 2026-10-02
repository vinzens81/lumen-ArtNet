package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.SceneNumber;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ProjectTransferService implements TransferProjectUseCase {

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final SceneRepository scenes;
	private final Clock clock;
	private final PatchChangeListener listener;

	public ProjectTransferService(ProjectRepository projects, UniverseRepository universes,
			FixtureRepository fixtures, GroupRepository groups, SceneRepository scenes, Clock clock,
			PatchChangeListener listener) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	@Override
	public ProjectExport export(long projectId) {
		var project = projects.findById(projectId).orElseThrow(() -> ProjectService.notFound(projectId));
		return new ProjectExport(project.spec(), universes.findByProject(projectId),
				fixtures.findByProject(projectId), groups.findByProject(projectId), scenes.findByProject(projectId));
	}

	@Override
	public Project importProject(ProjectExport export) {
		validateReferences(export);
		var created = projects.create(withUniqueName(export.project()), clock.instant());
		try {
			copyContents(created.id(), export);
		} catch (RuntimeException e) {
			projects.delete(created.id()); // no half-imported project
			throw e;
		}
		if (projects.findAll().stream().noneMatch(Project::active)) {
			projects.activate(created.id());
		}
		listener.patchChanged();
		return projects.findById(created.id()).orElseThrow();
	}

	private void copyContents(long projectId, ProjectExport export) {
		var universeIds = new HashMap<Long, Long>();
		for (var universe : export.universes()) {
			universeIds.put(universe.id(), universes.create(projectId, universe.spec()).id());
		}
		var fixtureIds = new HashMap<Long, Long>();
		for (var fixture : export.fixtures()) {
			var spec = fixture.spec();
			var copy = new FixtureSpec(spec.name(), spec.shortName(), spec.fixtureTypeId(), spec.modeName(),
					universeIds.get(spec.universeId()), spec.address(), spec.position(), spec.sortOrder());
			fixtureIds.put(fixture.id(), fixtures.create(projectId, copy).id());
		}
		for (var group : export.groups()) {
			var spec = group.spec();
			groups.create(projectId, new GroupSpec(spec.name(), spec.color(), spec.sortOrder(),
					spec.fixtureIds().stream().map(fixtureIds::get).toList()));
		}
		for (var scene : export.scenes()) {
			var values = new HashMap<Long, FixtureValues>();
			scene.values().forEach((fixtureId, fixtureValues) -> values.put(fixtureIds.get(fixtureId), fixtureValues));
			scenes.create(projectId, scene.spec(), values);
		}
	}

	private static void validateReferences(ProjectExport export) {
		var errors = new ArrayList<String>();
		var universeIds = new HashSet<Long>();
		export.universes().forEach(u -> {
			if (!universeIds.add(u.id())) {
				errors.add("Universe id " + u.id() + " is used twice");
			}
		});
		var fixtureIds = new HashSet<Long>();
		for (var fixture : export.fixtures()) {
			if (!fixtureIds.add(fixture.id())) {
				errors.add("Fixture id " + fixture.id() + " is used twice");
			}
			if (!universeIds.contains(fixture.spec().universeId())) {
				errors.add("Fixture '" + fixture.spec().name() + "' refers to unknown universe "
						+ fixture.spec().universeId());
			}
		}
		for (var group : export.groups()) {
			group.spec().fixtureIds().stream().filter(id -> !fixtureIds.contains(id)).forEach(id ->
					errors.add("Group '" + group.spec().name() + "' refers to unknown fixture " + id));
		}
		var sceneNumbers = new HashSet<SceneNumber>();
		for (var scene : export.scenes()) {
			if (!sceneNumbers.add(scene.spec().number())) {
				errors.add("Scene number " + scene.spec().number() + " is used twice");
			}
			scene.values().keySet().stream().filter(id -> !fixtureIds.contains(id)).forEach(id ->
					errors.add("Scene " + scene.spec().number() + " refers to unknown fixture " + id));
		}
		if (!errors.isEmpty()) {
			throw new DomainValidationException(List.copyOf(errors));
		}
	}

	/** Appends "(imported)", "(imported 2)", … if a project with the same name exists. */
	private ProjectSpec withUniqueName(ProjectSpec spec) {
		var names = new HashSet<String>();
		projects.findAll().forEach(p -> names.add(p.spec().name().toLowerCase(Locale.ROOT)));
		var name = spec.name();
		for (int i = 1; names.contains(name.toLowerCase(Locale.ROOT)); i++) {
			name = spec.name() + (i == 1 ? " (imported)" : " (imported " + i + ")");
		}
		return new ProjectSpec(name, spec.venue(), spec.fadeIn(), spec.fadeOut());
	}
}
