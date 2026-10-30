package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.composition.PatchAttributes;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.legacy.LegacyConverter;
import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyComposition;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyKey;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyScene;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.application.preset.port.out.PresetRepository;
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
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.NamedLook;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.party.PartyPage;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SectionTiming;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ProjectTransferService implements TransferProjectUseCase {

	private static final System.Logger LOG = System.getLogger(ProjectTransferService.class.getName());

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final LookRepository looks;
	private final CompositionRepository compositions;
	private final SceneRepository scenes;
	private final StagePlanRepository stagePlans;
	private final PartyMatrixRepository partyMatrices;
	private final PresetRepository presets;
	private final FaderRepository faders;
	private final FigureRepository figures;
	private final MoveRepository moves;
	private final QueryFixtureLibraryUseCase library;
	private final Clock clock;
	private final PatchChangeListener listener;

	public ProjectTransferService(ProjectRepository projects, UniverseRepository universes,
			FixtureRepository fixtures, GroupRepository groups, LookRepository looks, CompositionRepository compositions,
			SceneRepository scenes, StagePlanRepository stagePlans, PartyMatrixRepository partyMatrices,
			PresetRepository presets, FaderRepository faders, FigureRepository figures, MoveRepository moves,
			QueryFixtureLibraryUseCase library, Clock clock, PatchChangeListener listener) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.stagePlans = Objects.requireNonNull(stagePlans, "stagePlans");
		this.partyMatrices = Objects.requireNonNull(partyMatrices, "partyMatrices");
		this.presets = Objects.requireNonNull(presets, "presets");
		this.faders = Objects.requireNonNull(faders, "faders");
		this.figures = Objects.requireNonNull(figures, "figures");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.library = Objects.requireNonNull(library, "library");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	@Override
	public ProjectExport export(long projectId) {
		var project = projects.findById(projectId).orElseThrow(() -> ProjectService.notFound(projectId));
		return new ProjectExport(project.spec(), universes.findByProject(projectId),
				fixtures.findByProject(projectId), groups.findByProject(projectId), looks.findByProject(projectId),
				compositions.findByProject(projectId), scenes.findByProject(projectId), stagePlans.find(projectId),
				partyMatrices.find(projectId), presets.findByProject(projectId),
				faders.findByProject(projectId), figures.findByProject(projectId), moves.findByProject(projectId),
				LegacyShow.EMPTY);
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
		var ids = new Ids();
		for (var universe : export.universes()) {
			ids.universes.put(universe.id(), universes.create(projectId, universe.spec()).id());
		}
		for (var fixture : export.fixtures()) {
			var spec = fixture.spec();
			var copy = spec.withUniverseId(ids.universes.get(spec.universeId()));
			ids.fixtures.put(fixture.id(), fixtures.create(projectId, copy).id());
		}
		for (var group : export.groups()) {
			var spec = group.spec();
			ids.groups.put(group.id(), groups.create(projectId, new GroupSpec(spec.name(), spec.color(),
					spec.sortOrder(), spec.fixtureIds().stream().map(ids.fixtures::get).toList())).id());
		}
		var name = export.project().name();
		for (var preset : export.presets()) {
			ids.presets.put(preset.id(), presets.create(projectId, preset.spec(),
					ids.values(preset.values())).id());
		}
		for (var figure : export.figures()) {
			ids.figures.put(figure.id(), figures.create(projectId, figure.spec()).id());
		}
		for (var move : export.moves()) {
			var spec = move.spec();
			var fixtureList = spec.fixtures().stream()
					.filter(f -> ids.fixtures.containsKey(f.fixtureId()))
					.map(f -> new MoveFixture(ids.fixtures.get(f.fixtureId()), f.shiftX(), f.shiftY(), f.mirrorX(),
							f.mirrorY(), f.reverse()))
					.toList();
			var copy = new MoveSpec(spec.name(), ids.figures.get(spec.figureId()),
					fixtureList, spec.sizeX(), spec.sizeY(), spec.shiftX(), spec.shiftY(), spec.rotationDegrees(),
					spec.durationSeconds(), spec.repeat(), spec.stepOffset(), spec.onceStart());
			ids.moves.put(move.id(), moves.create(projectId, copy).id());
		}
		export.stagePlan().ifPresent(plan -> stagePlans.save(projectId, plan));

		var writer = new ShowContentWriter(looks, compositions, scenes, faders);
		var sceneIds = new HashMap<Long, Long>();
		if (isLegacy(export)) {
			var legacy = ids.legacy(export.legacy());
			for (var scene : legacy.scenes()) {
				sceneIds.put(scene.id(), scenes.create(projectId, scene.spec(), List.of(), List.of()).id());
			}
			var members = new HashMap<Long, List<Long>>();
			groups.findByProject(projectId).forEach(group -> members.put(group.id(), group.spec().fixtureIds()));
			var converted = LegacyConverter.convert(legacy, members,
					PatchAttributes.of(fixtures.findByProject(projectId), library));
			converted.report().forEach(line -> LOG.log(Level.INFO, "Import of \"{0}\": {1}",
					export.project().name(), line));
			writer.write(projectId, converted.content(), sceneIds);
			writer.removeScenes(converted.content(), sceneIds);
		} else {
			for (var scene : export.scenes()) {
				sceneIds.put(scene.id(), scenes.create(projectId, scene.spec(), List.of(), List.of()).id());
			}
			var sceneCompositions = new HashMap<Long, List<SceneComposition>>();
			var sceneKeys = new HashMap<Long, List<SceneKey>>();
			export.scenes().forEach(scene -> {
				sceneCompositions.put(scene.id(), scene.compositions());
				sceneKeys.put(scene.id(), scene.keys());
			});
			var importedLooks = export.looks().stream().map(look -> withoutPositions(name, look)).map(ids::look)
					.toList();
			var content = new ShowContent(importedLooks,
					export.compositions().stream().map(ids::composition).toList(),
					sceneCompositions, sceneKeys, export.faders(), Set.of());
			var written = writer.write(projectId, content, sceneIds, ids.moves);
			for (var scene : export.scenes()) {
				var id = sceneIds.get(scene.id());
				if (!scene.looks().isEmpty() || !scene.moves().isEmpty()) {
					scenes.replaceSection(id, "", mappedUses(scene.compositions(), written.compositionIds()),
							mappedLooks(scene.looks(), written.lookIds()), mappedMoves(scene.moves(), ids.moves),
							SectionTiming.NONE);
				}
				for (var section : scene.sections()) {
					scenes.replaceSection(id, section.marker(),
							mappedUses(section.compositions(), written.compositionIds()),
							mappedLooks(section.looks(), written.lookIds()), mappedMoves(section.moves(), ids.moves),
							section.timing());
				}
			}
			partyMatrices.save(projectId, partyMatrix(export.partyMatrix(), sceneIds, written.compositionIds(),
					ids.moves, ids.fixtures));
		}
	}

	private static List<SceneComposition> mappedUses(List<SceneComposition> uses, Map<Long, Long> compositionIds) {
		return uses.stream().filter(use -> compositionIds.containsKey(use.compositionId()))
				.map(use -> use.withCompositionId(compositionIds.get(use.compositionId())))
				.toList();
	}

	/** A look of an older file: pan and tilt are left out, positions come only from moves now. */
	private static NamedLook withoutPositions(String project, NamedLook look) {
		if (!look.look().hasPositions()) {
			return look;
		}
		LOG.log(Level.WARNING, "Import of \"{0}\": pan and tilt of look \"{1}\" left out – positions are moves now",
				project, look.spec().name());
		return new NamedLook(look.id(), look.projectId(), look.spec(), look.look().withoutPositions());
	}

	private static List<SceneMove> mappedMoves(List<SceneMove> uses, Map<Long, Long> moveIds) {
		return uses.stream().filter(use -> moveIds.containsKey(use.moveId()))
				.map(use -> new SceneMove(moveIds.get(use.moveId())))
				.toList();
	}

	private static List<SceneLook> mappedLooks(List<SceneLook> uses, Map<Long, Long> lookIds) {
		return uses.stream().filter(use -> lookIds.containsKey(use.lookId()))
				.map(use -> new SceneLook(lookIds.get(use.lookId()), use.level()))
				.toList();
	}

	/**
	 * The matrix with the new ids; a field whose scene, composition or move is not in the export shows only its text,
	 * and so does a gradient none of whose fixtures is in it.
	 */
	private static PartyMatrix partyMatrix(PartyMatrix matrix, Map<Long, Long> sceneIds,
			Map<Long, Long> compositionIds, Map<Long, Long> moveIds, Map<Long, Long> fixtureIds) {
		return new PartyMatrix(matrix.pages().stream()
				.map(page -> new PartyPage(page.name(), page.rows(), page.columns(), page.separators(),
						page.cells().stream()
								.map(cell -> partyCell(cell, sceneIds, compositionIds, moveIds, fixtureIds))
								.toList()))
				.toList());
	}

	private static PartyCell partyCell(PartyCell cell, Map<Long, Long> sceneIds, Map<Long, Long> compositionIds,
			Map<Long, Long> moveIds, Map<Long, Long> fixtureIds) {
		var fader = cell.fader().flatMap(gradient -> {
			var mapped = gradient.fixtureIds().stream().filter(fixtureIds::containsKey).map(fixtureIds::get).toList();
			return mapped.isEmpty() ? Optional.empty() : Optional.of(gradient.withFixtureIds(mapped));
		});
		return new PartyCell(cell.row(), cell.column(), cell.label(), cell.sceneId().map(sceneIds::get),
				cell.compositionId().map(compositionIds::get), cell.moveId().map(moveIds::get), cell.color(),
				cell.borderColor(), cell.mode(), cell.group(), cell.span(), cell.rowSpan(), fader);
	}

	private static boolean isLegacy(ProjectExport export) {
		return !export.legacy().scenes().isEmpty() || !export.legacy().compositions().isEmpty()
				|| !export.legacy().keys().isEmpty();
	}

	/** Ids of the export → ids in the new project. */
	private static final class Ids {

		final Map<Long, Long> universes = new HashMap<>();
		final Map<Long, Long> fixtures = new HashMap<>();
		final Map<Long, Long> groups = new HashMap<>();
		final Map<Long, Long> presets = new HashMap<>();
		final Map<Long, Long> figures = new HashMap<>();
		final Map<Long, Long> moves = new HashMap<>();

		Map<Long, FixtureValues> values(Map<Long, FixtureValues> values) {
			var result = new HashMap<Long, FixtureValues>();
			values.forEach((fixtureId, fixtureValues) -> result.put(fixtures.get(fixtureId), fixtureValues));
			return result;
		}

		Look look(Look look) {
			var refs = new HashMap<Long, Map<AttributeGroup, Long>>();
			look.presets().refs().forEach((fixtureId, byGroup) -> {
				var mapped = new HashMap<AttributeGroup, Long>();
				byGroup.forEach((group, presetId) -> {
					if (presets.containsKey(presetId)) {
						mapped.put(group, presets.get(presetId));
					}
				});
				refs.put(fixtures.get(fixtureId), mapped);
			});
			return new Look(values(look.values()), new PresetRefs(refs));
		}

		NamedLook look(NamedLook look) {
			return new NamedLook(look.id(), 0, look.spec(), look(look.look()));
		}

		Composition composition(Composition composition) {
			var spec = composition.spec();
			var generator = spec.generator().map(settings -> new GeneratorSettings(settings.effect(),
					target(settings.target()), settings.perHead()));
			return new Composition(composition.id(), 0, new CompositionSpec(
					spec.name(), spec.fadeSeconds(), spec.holdSeconds(), spec.playMode(), spec.intensityMode(),
					generator, spec.folder()), composition.steps());
		}

		Target target(Target target) {
			return switch (target) {
				case Target.Group group -> new Target.Group(groups.getOrDefault(group.groupId(), -1L));
				case Target.Fixtures list -> new Target.Fixtures(list.fixtureIds().stream().map(fixtures::get).toList());
			};
		}

		LegacyShow legacy(LegacyShow show) {
			var legacyScenes = show.scenes().stream()
					.map(scene -> new LegacyScene(scene.id(), scene.spec(), look(scene.look()), scene.outsideCueList()))
					.toList();
			var legacyCompositions = show.compositions().stream().map(this::composition).toList();
			var keys = show.keys().stream()
					.map(key -> new LegacyKey(key.slot(), key.mode(), key.fade(), key.composition().map(this::composition),
							key.sceneId()))
					.toList();
			return new LegacyShow(legacyScenes, legacyCompositions, keys, show.sceneKeys(), show.faders());
		}

		LegacyComposition composition(LegacyComposition c) {
			Pattern pattern = c.pattern();
			if (pattern instanceof StepSequence sequence) {
				pattern = new StepSequence(sequence.steps().stream()
						.map(step -> new StepSequence.Step(step.holdSeconds(), step.fadeSeconds(), values(step.values())))
						.toList(), sequence.playMode(), sequence.intensityMode());
			}
			return new LegacyComposition(c.id(), c.sceneId(), c.sourceId(), c.position(), c.name(), pattern,
					c.target().map(this::target), c.level(), c.speedFactor(), c.restart(), c.once(), c.perHead());
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
		var groupIds = new HashSet<Long>();
		export.groups().forEach(g -> groupIds.add(g.id()));
		var presetIds = new HashSet<Long>();
		for (var preset : export.presets()) {
			if (!presetIds.add(preset.id())) {
				errors.add("Preset id " + preset.id() + " is used twice");
			}
			preset.values().keySet().stream().filter(id -> !fixtureIds.contains(id)).forEach(id ->
					errors.add("Preset '" + preset.spec().name() + "' refers to unknown fixture " + id));
		}
		var compositionIds = new HashSet<Long>();
		export.compositions().forEach(c -> {
			if (!compositionIds.add(c.id())) {
				errors.add("Composition id " + c.id() + " is used twice");
			}
		});
		var lookIds = new HashSet<Long>();
		for (var look : export.looks()) {
			var name = "Look '" + look.spec().name() + "'";
			if (!lookIds.add(look.id())) {
				errors.add("Look id " + look.id() + " is used twice");
			}
			look.spec().owner().filter(id -> !compositionIds.contains(id))
					.ifPresent(id -> errors.add(name + " belongs to unknown composition " + id));
			validateLook(name, look.look(), fixtureIds, presetIds, errors);
		}
		for (var composition : export.compositions()) {
			var name = "Composition '" + composition.spec().name() + "'";
			composition.lookIds().stream().filter(id -> !lookIds.contains(id)).distinct()
					.forEach(id -> errors.add(name + " plays unknown look " + id));
			composition.spec().generator().ifPresent(settings ->
					validateTarget(name, settings.target(), groupIds, fixtureIds, errors));
		}
		var figureIds = new HashSet<Long>();
		export.figures().forEach(f -> {
			if (!figureIds.add(f.id())) {
				errors.add("Figure id " + f.id() + " is used twice");
			}
		});
		var moveIds = new HashSet<Long>();
		for (var move : export.moves()) {
			var name = "Move '" + move.spec().name() + "'";
			if (!moveIds.add(move.id())) {
				errors.add("Move id " + move.id() + " is used twice");
			}
			if (!figureIds.contains(move.spec().figureId())) {
				errors.add(name + " refers to unknown figure " + move.spec().figureId());
			}
			move.spec().fixtureIds().stream().filter(id -> !fixtureIds.contains(id))
					.forEach(id -> errors.add(name + " refers to unknown fixture " + id));
		}
		var sceneIds = new HashSet<Long>();
		var sceneNumbers = new HashSet<SceneNumber>();
		for (var scene : export.scenes()) {
			sceneIds.add(scene.id());
			if (!sceneNumbers.add(scene.spec().number())) {
				errors.add("Scene number " + scene.spec().number() + " is used twice");
			}
			scene.compositions().stream().filter(use -> !compositionIds.contains(use.compositionId())).forEach(use ->
					errors.add("Scene " + scene.spec().number() + " plays unknown composition " + use.compositionId()));
			scene.keys().forEach(key -> {
				key.compositionId().filter(id -> !compositionIds.contains(id)).ifPresent(id -> errors.add("Key "
						+ key.slot() + " of scene " + scene.spec().number() + " plays unknown composition " + id));
				key.moveId().filter(id -> !moveIds.contains(id)).ifPresent(id -> errors.add("Key "
						+ key.slot() + " of scene " + scene.spec().number() + " plays unknown move " + id));
			});
			scene.moves().stream().filter(use -> !moveIds.contains(use.moveId())).forEach(use ->
					errors.add("Scene " + scene.spec().number() + " plays unknown move " + use.moveId()));
			scene.looks().stream().filter(use -> !lookIds.contains(use.lookId())).forEach(use ->
					errors.add("Scene " + scene.spec().number() + " plays unknown look " + use.lookId()));
			for (var section : scene.sections()) {
				var where = "Scene " + scene.spec().number() + " at marker \"" + section.marker() + "\"";
				section.compositions().stream().filter(use -> !compositionIds.contains(use.compositionId()))
						.forEach(use -> errors.add(where + " plays unknown composition " + use.compositionId()));
				section.looks().stream().filter(use -> !lookIds.contains(use.lookId()))
						.forEach(use -> errors.add(where + " plays unknown look " + use.lookId()));
				section.moves().stream().filter(use -> !moveIds.contains(use.moveId()))
						.forEach(use -> errors.add(where + " plays unknown move " + use.moveId()));
			}
		}
		validateLegacy(export.legacy(), fixtureIds, groupIds, presetIds, sceneIds, sceneNumbers, errors);
		var faderSlots = new HashSet<Integer>();
		for (var fader : export.faders()) {
			if (!faderSlots.add(fader.slot())) {
				errors.add("Fader " + fader.slot() + " is used twice");
			}
			if (!compositionIds.contains(fader.compositionId())) {
				errors.add("Fader " + fader.slot() + " plays unknown composition " + fader.compositionId());
			}
		}
		for (var fader : export.legacy().faders()) {
			if (!faderSlots.add(fader.slot())) {
				errors.add("Fader " + fader.slot() + " is used twice");
			}
			if (!sceneIds.contains(fader.sceneId())) {
				errors.add("Fader " + fader.slot() + " refers to unknown scene " + fader.sceneId());
			}
		}
		if (!errors.isEmpty()) {
			throw new DomainValidationException(List.copyOf(errors));
		}
	}

	/** Scenes of an old file count as scenes of the export (faders refer to them). */
	private static void validateLegacy(LegacyShow legacy, Set<Long> fixtureIds, Set<Long> groupIds,
			Set<Long> presetIds, Set<Long> sceneIds, Set<SceneNumber> sceneNumbers, List<String> errors) {
		for (var scene : legacy.scenes()) {
			sceneIds.add(scene.id());
			if (!sceneNumbers.add(scene.spec().number())) {
				errors.add("Scene number " + scene.spec().number() + " is used twice");
			}
			validateLook("Scene " + scene.spec().number(), scene.look(), fixtureIds, presetIds, errors);
		}
		var libraryIds = new HashSet<Long>();
		legacy.compositions().stream().filter(c -> c.sceneId().isEmpty()).forEach(c -> libraryIds.add(c.id()));
		var all = new ArrayList<>(legacy.compositions());
		legacy.keys().forEach(key -> key.composition().ifPresent(all::add));
		for (var composition : all) {
			var name = "Composition '" + composition.name() + "'";
			composition.sceneId().filter(id -> !sceneIds.contains(id))
					.ifPresent(id -> errors.add(name + " refers to unknown scene " + id));
			composition.sourceId().filter(id -> !libraryIds.contains(id))
					.ifPresent(id -> errors.add(name + " refers to unknown library composition " + id));
			composition.target().ifPresent(target -> validateTarget(name, target, groupIds, fixtureIds, errors));
			composition.pattern().ownFixtures().orElse(List.of()).stream().filter(id -> !fixtureIds.contains(id))
					.forEach(id -> errors.add(name + " refers to unknown fixture " + id));
		}
		var slots = new HashSet<Integer>();
		for (var key : legacy.keys()) {
			if (!slots.add(key.slot())) {
				errors.add("Extra key " + key.slot() + " is used twice");
			}
			key.sceneId().filter(id -> !sceneIds.contains(id)).ifPresent(id ->
					errors.add("Extra key " + key.slot() + " refers to unknown scene " + id));
		}
		legacy.sceneKeys().keySet().stream().filter(id -> !sceneIds.contains(id))
				.forEach(id -> errors.add("Extra keys refer to unknown scene " + id));
	}

	private static void validateLook(String name, Look look, Set<Long> fixtureIds, Set<Long> presetIds,
			List<String> errors) {
		look.values().keySet().stream().filter(id -> !fixtureIds.contains(id)).forEach(id ->
				errors.add(name + " refers to unknown fixture " + id));
		look.presets().refs().forEach((fixtureId, byGroup) -> byGroup.values().stream()
				.filter(id -> !presetIds.contains(id))
				.forEach(id -> errors.add(name + " refers to unknown preset " + id)));
	}

	private static void validateTarget(String name, Target target, Set<Long> groupIds, Set<Long> fixtureIds,
			List<String> errors) {
		switch (target) {
			case Target.Group group -> {
				if (!groupIds.contains(group.groupId())) {
					errors.add(name + " refers to unknown group " + group.groupId());
				}
			}
			case Target.Fixtures list -> list.fixtureIds().stream().filter(id -> !fixtureIds.contains(id))
					.forEach(id -> errors.add(name + " refers to unknown fixture " + id));
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
		return new ProjectSpec(name, spec.venue(), spec.fadeIn(), spec.fadeOut(), spec.fadeCurve(), spec.dimOut(),
				spec.travel());
	}
}
