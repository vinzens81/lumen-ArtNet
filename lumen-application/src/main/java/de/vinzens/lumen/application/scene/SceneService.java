package de.vinzens.lumen.application.scene;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.CompositionResolver;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase;
import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneSection;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import de.vinzens.lumen.domain.scene.SectionTiming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class SceneService implements ManageScenesUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final CompositionRepository compositions;
	private final LookRepository looks;
	private final MoveRepository moves;
	private final ManageLooksUseCase lookUseCase;
	private final CompositionResolver resolver;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;

	public SceneService(ProjectRepository projects, SceneRepository scenes, CompositionRepository compositions,
			LookRepository looks, MoveRepository moves, ManageLooksUseCase lookUseCase, CompositionResolver resolver,
			ControlProgrammerUseCase programmer, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.moves = Objects.requireNonNull(moves, "moves");
		this.lookUseCase = Objects.requireNonNull(lookUseCase, "lookUseCase");
		this.resolver = Objects.requireNonNull(resolver, "resolver");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Scene> scenes(long projectId) {
		requireProject(projectId);
		return scenes.findByProject(projectId);
	}

	@Override
	public Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, SceneTiming timing, String script, boolean recordFromProgrammer) {
		if (recordFromProgrammer) {
			requireActive(projectId);
		} else {
			requireProject(projectId);
		}
		var existing = scenes.findByProject(projectId);
		var sceneNumber = number.orElseGet(() -> existing.isEmpty()
				? SceneNumber.of(1)
				: existing.getLast().spec().number().nextWhole());
		requireFreeNumber(existing, sceneNumber, null);
		var spec = new SceneSpec(sceneNumber, name, cueNote, autoFollow, timing, script);
		var content = recordFromProgrammer
				? List.of(SceneComposition.of(recordedComposition(projectId, spec.name())))
				: List.<SceneComposition>of();
		return published(scenes.create(projectId, spec, content, inheritedKeys(existing, spec)));
	}

	/** The quick way: the programmer as a composition with one step that plays its own look. */
	private long recordedComposition(long projectId, String name) {
		var composition = compositions.create(projectId, CompositionSpec.of(name), List.of());
		var look = looks.create(projectId, new LookSpec(name, Optional.of(composition.id())),
				programmer.programmerLook().withoutPositions());
		compositions.update(composition.id(), composition.spec(), List.of(CompositionStep.of(look.id())));
		events.publish(StateTopic.LOOKS);
		events.publish(StateTopic.COMPOSITIONS);
		return composition.id();
	}

	/** A new scene takes the keys of the scene before it, so inserting a scene keeps e.g. a storm key. */
	private static List<SceneKey> inheritedKeys(List<Scene> existing, SceneSpec created) {
		return existing.stream()
				.filter(scene -> scene.spec().number().compareTo(created.number()) < 0)
				.reduce((first, second) -> second)
				.map(Scene::keys)
				.orElse(List.of());
	}

	@Override
	public Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, SceneTiming timing, String script) {
		requireScene(projectId, sceneId);
		requireFreeNumber(scenes.findByProject(projectId), number, sceneId);
		return published(scenes.updateSpec(sceneId,
				new SceneSpec(number, name, cueNote, autoFollow, timing, script)));
	}

	@Override
	public Scene duplicate(long projectId, long sceneId, Optional<SceneNumber> number) {
		var original = requireScene(projectId, sceneId);
		var existing = scenes.findByProject(projectId);
		var sceneNumber = number.orElseGet(() -> existing.getLast().spec().number().nextWhole());
		requireFreeNumber(existing, sceneNumber, null);
		var spec = original.spec();
		var copy = scenes.create(projectId, new SceneSpec(sceneNumber, spec.name() + " (copy)", spec.cueNote(),
				spec.autoFollow(), spec.timing(), spec.script()), original.compositions(), original.keys());
		if (!original.looks().isEmpty() || !original.moves().isEmpty()) {
			copy = scenes.replaceSection(copy.id(), "", original.compositions(), original.looks(), original.moves(),
					SectionTiming.NONE);
		}
		for (var section : original.sections()) {
			copy = scenes.replaceSection(copy.id(), section.marker(), section.compositions(), section.looks(),
					section.moves(), section.timing());
		}
		return published(copy);
	}

	@Override
	public Scene setCompositions(long projectId, long sceneId, List<SceneComposition> sceneCompositions) {
		var scene = requireScene(projectId, sceneId);
		requireCompositions(projectId, sceneCompositions.stream().map(SceneComposition::compositionId));
		new Scene(scene.id(), projectId, scene.spec(), sceneCompositions, scene.keys()); // validates
		return published(scenes.replaceCompositions(sceneId, sceneCompositions));
	}

	@Override
	public Scene setSection(long projectId, long sceneId, String marker, List<SceneComposition> sceneCompositions,
			List<SceneLook> sceneLooks, List<SceneMove> sceneMoves, SectionTiming timing) {
		var scene = requireScene(projectId, sceneId);
		var name = marker == null ? "" : marker.strip();
		requireCompositions(projectId, sceneCompositions.stream().map(SceneComposition::compositionId));
		requireLooks(projectId, sceneLooks.stream().map(SceneLook::lookId));
		requireMoves(projectId, sceneMoves.stream().map(SceneMove::moveId));
		// validates
		if (name.isEmpty()) {
			scene.withStart(sceneCompositions, sceneLooks, sceneMoves);
		} else {
			scene.withSection(new SceneSection(name, sceneCompositions, sceneLooks, sceneMoves, timing));
		}
		return published(scenes.replaceSection(sceneId, name, sceneCompositions, sceneLooks, sceneMoves,
				name.isEmpty() ? SectionTiming.NONE : timing));
	}

	@Override
	public Scene setKeys(long projectId, long sceneId, List<SceneKey> keys) {
		var scene = requireScene(projectId, sceneId);
		requireCompositions(projectId, keys.stream().flatMap(key -> key.compositionId().stream()));
		requireMoves(projectId, keys.stream().flatMap(key -> key.moveId().stream()));
		new Scene(scene.id(), projectId, scene.spec(), scene.compositions(), keys); // validates
		return published(scenes.replaceKeys(sceneId, keys));
	}

	@Override
	public Scene record(long projectId, long sceneId, RecordOptions options) {
		requireActive(projectId);
		var scene = requireScene(projectId, sceneId);
		if (scene.compositions().isEmpty()) {
			if (options.merge()) {
				throw new ConflictException("The scene has nothing to merge into yet – record it first");
			}
			var created = recordedComposition(projectId, scene.spec().name());
			return published(scenes.replaceCompositions(sceneId, List.of(SceneComposition.of(created))));
		}
		var first = compositions.findById(scene.compositions().getFirst().compositionId()).orElseThrow();
		if (first.steps().size() != 1) {
			throw new ConflictException("\"" + first.spec().name() + "\" has " + first.steps().size()
					+ " steps – record its looks in the composition");
		}
		lookUseCase.record(projectId, first.steps().getFirst().lookId(), options);
		return scene;
	}

	@Override
	public void loadIntoProgrammer(long projectId, long sceneId) {
		requireActive(projectId);
		requireScene(projectId, sceneId);
		programmer.loadLook(Look.of(resolver.sceneAtStart(projectId, sceneId)));
	}

	@Override
	public void delete(long projectId, long sceneId) {
		requireScene(projectId, sceneId);
		scenes.delete(sceneId);
		events.publish(StateTopic.SCENES);
	}

	private void requireCompositions(long projectId, Stream<Long> ids) {
		var problems = new ArrayList<String>();
		ids.distinct().forEach(id -> {
			if (compositions.findById(id).filter(c -> c.projectId() == projectId).isEmpty()) {
				problems.add("composition " + id + " does not exist in this project");
			}
		});
		if (!problems.isEmpty()) {
			throw new DomainValidationException(problems);
		}
	}

	private void requireMoves(long projectId, Stream<Long> ids) {
		var problems = new ArrayList<String>();
		ids.distinct().forEach(id -> {
			if (moves.findById(id).filter(m -> m.projectId() == projectId).isEmpty()) {
				problems.add("move " + id + " does not exist in this project");
			}
		});
		if (!problems.isEmpty()) {
			throw new DomainValidationException(problems);
		}
	}

	private void requireLooks(long projectId, Stream<Long> ids) {
		var problems = new ArrayList<String>();
		ids.distinct().forEach(id -> {
			if (looks.findById(id).filter(l -> l.projectId() == projectId).isEmpty()) {
				problems.add("look " + id + " does not exist in this project");
			}
		});
		if (!problems.isEmpty()) {
			throw new DomainValidationException(problems);
		}
	}

	private void requireFreeNumber(List<Scene> existing, SceneNumber number, Long ownId) {
		var taken = existing.stream()
				.filter(scene -> scene.spec().number().equals(number) && !Objects.equals(scene.id(), ownId))
				.findFirst();
		taken.ifPresent(scene -> {
			throw new ConflictException("Scene " + number + " already exists (\"" + scene.spec().name() + "\")");
		});
	}

	private Scene requireScene(long projectId, long sceneId) {
		requireProject(projectId);
		return scenes.findById(sceneId)
				.filter(scene -> scene.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + sceneId + " does not exist in project " + projectId));
	}

	private Project requireProject(long projectId) {
		return projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}

	private void requireActive(long projectId) {
		if (!requireProject(projectId).active()) {
			throw new ConflictException("The programmer belongs to the active project – activate project "
					+ projectId + " first");
		}
	}

	private Scene published(Scene scene) {
		events.publish(StateTopic.SCENES);
		return scene;
	}
}
