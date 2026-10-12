package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.ManageCompositionsUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.FixtureAttributes;
import de.vinzens.lumen.domain.composition.Generator;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class CompositionService implements ManageCompositionsUseCase {

	private final ProjectRepository projects;
	private final CompositionRepository compositions;
	private final LookRepository looks;
	private final SceneRepository scenes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final QueryFixtureLibraryUseCase library;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;

	public CompositionService(ProjectRepository projects, CompositionRepository compositions, LookRepository looks,
			SceneRepository scenes, FixtureRepository fixtures, GroupRepository groups,
			QueryFixtureLibraryUseCase library, ControlProgrammerUseCase programmer, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.looks = Objects.requireNonNull(looks, "looks");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.library = Objects.requireNonNull(library, "library");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Composition> compositions(long projectId) {
		requireProject(projectId);
		return compositions.findByProject(projectId);
	}

	@Override
	public Composition composition(long projectId, long compositionId) {
		requireProject(projectId);
		return compositions.findById(compositionId)
				.filter(c -> c.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Composition " + compositionId + " does not exist in project "
						+ projectId));
	}

	@Override
	public Composition create(long projectId, CompositionSpec spec, List<CompositionStep> steps) {
		requireProject(projectId);
		Composition.validateSteps(spec, steps);
		requirePlayable(projectId, Optional.empty(), steps);
		return published(compositions.create(projectId, spec, steps));
	}

	@Override
	public Composition update(long projectId, long compositionId, CompositionSpec spec, List<CompositionStep> steps) {
		var composition = composition(projectId, compositionId);
		Composition.validateSteps(spec, steps);
		requirePlayable(projectId, Optional.of(compositionId), steps);
		var updated = compositions.update(compositionId, spec, steps);
		dropUnusedOwnLooks(composition, updated);
		return published(updated);
	}

	@Override
	public Composition duplicate(long projectId, long compositionId) {
		var original = composition(projectId, compositionId);
		var spec = original.spec().withName(original.spec().name() + " (copy)");
		var copy = compositions.create(projectId, spec, List.of());
		// own looks are copied, shared looks stay shared
		var copiedLooks = new HashMap<Long, Long>();
		for (var look : looks.findByProject(projectId)) {
			if (look.spec().owner().equals(Optional.of(compositionId))) {
				copiedLooks.put(look.id(), looks.create(projectId,
						new LookSpec(look.spec().name(), Optional.of(copy.id())), look.look()).id());
			}
		}
		var steps = original.steps().stream()
				.map(step -> step.withLook(copiedLooks.getOrDefault(step.lookId(), step.lookId())))
				.toList();
		events.publish(StateTopic.LOOKS);
		return published(compositions.update(copy.id(), spec, steps));
	}

	@Override
	public void delete(long projectId, long compositionId) {
		composition(projectId, compositionId);
		var users = scenes.findByProject(projectId).stream()
				.filter(scene -> scene.contains(compositionId)
						|| scene.keys().stream().anyMatch(key -> key.plays(compositionId)))
				.map(scene -> scene.spec().number().toString())
				.collect(Collectors.joining(", "));
		if (!users.isEmpty()) {
			throw new ConflictException("The composition is used in scene " + users + " – remove it from there first");
		}
		compositions.delete(compositionId);
		events.publish(StateTopic.LOOKS);
		events.publish(StateTopic.COMPOSITIONS);
	}

	@Override
	public Composition recordStep(long projectId, long compositionId) {
		requireActive(projectId);
		var composition = composition(projectId, compositionId);
		var look = looks.create(projectId, ownLookSpec(composition, composition.steps().size() + 1),
				programmer.programmerLook().withoutPositions());
		var steps = new ArrayList<>(composition.steps());
		steps.add(CompositionStep.of(look.id()));
		events.publish(StateTopic.LOOKS);
		return published(compositions.update(compositionId, composition.spec(), steps));
	}

	@Override
	public Composition generate(long projectId, long compositionId, GeneratorSettings settings) {
		var composition = composition(projectId, compositionId);
		var target = targetFixtures(projectId, settings.target());
		if (target.isEmpty()) {
			throw new DomainValidationException(List.of("the target has no fixtures"));
		}
		var generated = Generator.generate(settings.effect(), target, settings.perHead(), attributes(projectId));
		var steps = new ArrayList<CompositionStep>();
		for (var step : generated.steps()) {
			var look = looks.create(projectId, ownLookSpec(composition, steps.size() + 1), Look.of(step.values()));
			steps.add(new CompositionStep(look.id(), step.fadeSeconds(), step.holdSeconds()));
		}
		var old = composition.spec();
		var playMode = generated.playMode() == StepSequence.PlayMode.PING_PONG ? PlayMode.PING_PONG
				: old.playMode() == PlayMode.ONCE ? PlayMode.ONCE : PlayMode.LOOP;
		var spec = new CompositionSpec(old.name(), generated.fadeSeconds(), generated.holdSeconds(), playMode,
				generated.intensityMode(), Optional.of(settings), old.folder());
		var updated = compositions.update(compositionId, spec, steps);
		dropUnusedOwnLooks(composition, updated);
		events.publish(StateTopic.LOOKS);
		return published(updated);
	}

	/** Own looks that no step plays any more are deleted. */
	private void dropUnusedOwnLooks(Composition before, Composition after) {
		var used = new HashSet<>(after.lookIds());
		var dropped = false;
		for (var lookId : new HashSet<>(before.lookIds())) {
			if (used.contains(lookId)) {
				continue;
			}
			var look = looks.findById(lookId);
			if (look.isPresent() && look.get().spec().owner().equals(Optional.of(before.id()))) {
				looks.delete(lookId);
				dropped = true;
			}
		}
		if (dropped) {
			events.publish(StateTopic.LOOKS);
		}
	}

	/** Steps may play shared looks of the project and the composition's own looks. */
	private void requirePlayable(long projectId, Optional<Long> compositionId, List<CompositionStep> steps) {
		var problems = new ArrayList<String>();
		for (int i = 0; i < steps.size(); i++) {
			var lookId = steps.get(i).lookId();
			var look = looks.findById(lookId).filter(l -> l.projectId() == projectId);
			if (look.isEmpty()) {
				problems.add("step " + (i + 1) + ": look " + lookId + " does not exist in this project");
			} else if (look.get().spec().owner().isPresent() && !look.get().spec().owner().equals(compositionId)) {
				problems.add("step " + (i + 1) + ": \"" + look.get().spec().name()
						+ "\" belongs to another composition – make it shared first");
			}
		}
		if (!problems.isEmpty()) {
			throw new DomainValidationException(problems);
		}
	}

	private static LookSpec ownLookSpec(Composition composition, int stepNumber) {
		return new LookSpec(composition.spec().name() + " – Step " + stepNumber, Optional.of(composition.id()));
	}

	private List<Long> targetFixtures(long projectId, Target target) {
		var patched = fixtures.findByProject(projectId).stream().map(PatchedFixture::id).collect(Collectors.toSet());
		return switch (target) {
			case Target.Group group -> groups.findById(group.groupId())
					.filter(g -> g.projectId() == projectId)
					.orElseThrow(() -> new DomainValidationException(List.of("group " + group.groupId()
							+ " does not exist in this project")))
					.spec().fixtureIds();
			case Target.Fixtures list -> {
				var unknown = list.fixtureIds().stream().filter(id -> !patched.contains(id)).toList();
				if (!unknown.isEmpty()) {
					throw new DomainValidationException(List.of("fixtures " + unknown + " do not exist in this project"));
				}
				yield list.fixtureIds();
			}
		};
	}

	private FixtureAttributes attributes(long projectId) {
		return PatchAttributes.of(fixtures.findByProject(projectId), library);
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

	private Composition published(Composition composition) {
		events.publish(StateTopic.COMPOSITIONS);
		return composition;
	}
}
