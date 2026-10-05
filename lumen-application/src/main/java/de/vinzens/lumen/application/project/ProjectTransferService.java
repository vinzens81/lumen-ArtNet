package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.party.port.out.PartyButtonRepository;
import de.vinzens.lumen.application.project.port.in.TransferProjectUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.StagePlanRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
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
	private final StagePlanRepository stagePlans;
	private final CompositionRepository compositions;
	private final PartyButtonRepository partyButtons;
	private final Clock clock;
	private final PatchChangeListener listener;

	public ProjectTransferService(ProjectRepository projects, UniverseRepository universes,
			FixtureRepository fixtures, GroupRepository groups, SceneRepository scenes, StagePlanRepository stagePlans,
			CompositionRepository compositions, PartyButtonRepository partyButtons, Clock clock,
			PatchChangeListener listener) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.stagePlans = Objects.requireNonNull(stagePlans, "stagePlans");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.partyButtons = Objects.requireNonNull(partyButtons, "partyButtons");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	@Override
	public ProjectExport export(long projectId) {
		var project = projects.findById(projectId).orElseThrow(() -> ProjectService.notFound(projectId));
		return new ProjectExport(project.spec(), universes.findByProject(projectId),
				fixtures.findByProject(projectId), groups.findByProject(projectId), scenes.findByProject(projectId),
				stagePlans.find(projectId), allCompositions(projectId), compositions.findKeys(projectId),
				compositions.findSceneKeys(projectId), partyButtons.findByProject(projectId));
	}

	private List<Composition> allCompositions(long projectId) {
		var result = new ArrayList<>(compositions.findLibrary(projectId));
		compositions.findByScenes(projectId).values().forEach(result::addAll);
		return result;
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
		var groupIds = new HashMap<Long, Long>();
		for (var group : export.groups()) {
			var spec = group.spec();
			groupIds.put(group.id(), groups.create(projectId, new GroupSpec(spec.name(), spec.color(), spec.sortOrder(),
					spec.fixtureIds().stream().map(fixtureIds::get).toList())).id());
		}
		var sceneIds = new HashMap<Long, Long>();
		for (var scene : export.scenes()) {
			var values = new HashMap<Long, FixtureValues>();
			scene.values().forEach((fixtureId, fixtureValues) -> values.put(fixtureIds.get(fixtureId), fixtureValues));
			sceneIds.put(scene.id(), scenes.create(projectId, scene.spec(), values).id());
		}
		export.stagePlan().ifPresent(plan -> stagePlans.save(projectId, plan));

		// library first, so copies can refer to their origin
		var compositionIds = new HashMap<Long, Long>();
		var ordered = new ArrayList<>(export.compositions());
		ordered.sort((a, b) -> Boolean.compare(!a.inLibrary(), !b.inLibrary()));
		for (var composition : ordered) {
			var spec = remap(composition.spec(), fixtureIds, groupIds);
			var created = compositions.create(projectId, composition.sceneId().map(sceneIds::get),
					composition.sourceId().map(compositionIds::get), spec);
			compositionIds.put(composition.id(), created.id());
		}
		for (var key : export.keys()) {
			switch (key.content()) {
				case ExtraKey.Content.PlaysComposition plays -> compositions.saveKey(projectId, key.slot(),
						plays.composition().sourceId().map(compositionIds::get), key.mode(), key.fade(),
						remap(plays.composition().spec(), fixtureIds, groupIds));
				case ExtraKey.Content.PlaysScene plays -> compositions.saveSceneKey(projectId, key.slot(),
						sceneIds.get(plays.sceneId()), key.mode(), key.fade());
			}
		}
		export.sceneKeys().forEach((sceneId, slots) -> compositions.saveSceneKeys(sceneIds.get(sceneId), slots));
		for (var button : export.partyButtons()) {
			var values = new HashMap<Long, FixtureValues>();
			button.values().forEach((fixtureId, v) -> values.put(fixtureIds.get(fixtureId), v));
			partyButtons.create(projectId, button.name(), button.mode(), values);
		}
	}

	/** The spec with fixture and group ids of the new project. */
	private static CompositionSpec remap(CompositionSpec spec, Map<Long, Long> fixtureIds, Map<Long, Long> groupIds) {
		Target target = switch (spec.target()) {
			case Target.Group group -> new Target.Group(groupIds.get(group.groupId()));
			case Target.Fixtures list -> new Target.Fixtures(list.fixtureIds().stream().map(fixtureIds::get).toList());
			case Target.Steps steps -> steps;
		};
		Pattern pattern = spec.pattern();
		if (pattern instanceof StepSequence sequence) {
			pattern = new StepSequence(sequence.steps().stream().map(step -> {
				var values = new HashMap<Long, FixtureValues>();
				step.values().forEach((fixtureId, v) -> values.put(fixtureIds.get(fixtureId), v));
				return new StepSequence.Step(step.holdSeconds(), step.fadeSeconds(), values);
			}).toList(), sequence.playMode());
		}
		return spec.withContent(spec.name(), pattern, target);
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
		var groupIds = new HashSet<Long>();
		export.groups().forEach(g -> groupIds.add(g.id()));
		var sceneIds = new HashSet<Long>();
		export.scenes().forEach(scene -> sceneIds.add(scene.id()));
		var libraryIds = new HashSet<Long>();
		export.compositions().stream().filter(Composition::inLibrary).forEach(c -> libraryIds.add(c.id()));
		for (var composition : export.compositions()) {
			var name = "Composition '" + composition.spec().name() + "'";
			composition.sceneId().filter(id -> !sceneIds.contains(id))
					.ifPresent(id -> errors.add(name + " refers to unknown scene " + id));
			composition.sourceId().filter(id -> !libraryIds.contains(id))
					.ifPresent(id -> errors.add(name + " refers to unknown library composition " + id));
			switch (composition.spec().target()) {
				case Target.Group group -> {
					if (!groupIds.contains(group.groupId())) {
						errors.add(name + " refers to unknown group " + group.groupId());
					}
				}
				case Target.Fixtures list -> list.fixtureIds().stream().filter(id -> !fixtureIds.contains(id))
						.forEach(id -> errors.add(name + " refers to unknown fixture " + id));
				case Target.Steps steps -> composition.spec().pattern().ownFixtures().orElse(List.of()).stream()
						.filter(id -> !fixtureIds.contains(id))
						.forEach(id -> errors.add(name + " refers to unknown fixture " + id));
			}
		}
		var slots = new HashSet<Integer>();
		for (var key : export.keys()) {
			if (!slots.add(key.slot())) {
				errors.add("Extra key " + key.slot() + " is used twice");
			}
			key.composition().flatMap(Composition::sourceId).filter(id -> !libraryIds.contains(id)).ifPresent(id ->
					errors.add("Extra key " + key.slot() + " refers to unknown library composition " + id));
			key.sceneId().filter(id -> !sceneIds.contains(id)).ifPresent(id ->
					errors.add("Extra key " + key.slot() + " refers to unknown scene " + id));
		}
		export.sceneKeys().keySet().stream().filter(id -> !sceneIds.contains(id))
				.forEach(id -> errors.add("Extra keys refer to unknown scene " + id));
		for (var button : export.partyButtons()) {
			button.values().keySet().stream().filter(id -> !fixtureIds.contains(id)).forEach(id ->
					errors.add("Party button '" + button.name() + "' refers to unknown fixture " + id));
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
