package de.vinzens.lumen.application.scene;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.in.ManageScenesUseCase;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SceneService implements ManageScenesUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final ControlProgrammerUseCase programmer;
	private final CompositionRepository compositions;
	private final StateEvents events;

	public SceneService(ProjectRepository projects, SceneRepository scenes, ControlProgrammerUseCase programmer,
			CompositionRepository compositions, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Scene> scenes(long projectId) {
		requireProject(projectId);
		return scenes.findByProject(projectId);
	}

	@Override
	public Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing, boolean recordFromProgrammer) {
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
		var spec = new SceneSpec(sceneNumber, name, cueNote, autoFollow, keyOnly, timing);
		var values = recordFromProgrammer ? programmerSnapshot() : Map.<Long, FixtureValues>of();
		var created = scenes.create(projectId, spec, values);
		inheritExtraKeys(projectId, existing, created);
		return published(created);
	}

	/** A new scene can use the same extra keys as the scene before it, so inserting a scene keeps e.g. a storm key. */
	private void inheritExtraKeys(long projectId, List<Scene> existing, Scene created) {
		if (created.spec().keyOnly()) {
			return;
		}
		existing.stream()
				.filter(scene -> !scene.spec().keyOnly())
				.filter(scene -> scene.spec().number().compareTo(created.spec().number()) < 0)
				.reduce((first, second) -> second)
				.map(previous -> compositions.findSceneKeys(projectId).get(previous.id()))
				.filter(slots -> !slots.isEmpty())
				.ifPresent(slots -> compositions.saveSceneKeys(created.id(), slots));
	}

	@Override
	public Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing) {
		requireScene(projectId, sceneId);
		requireFreeNumber(scenes.findByProject(projectId), number, sceneId);
		return published(scenes.updateSpec(sceneId,
				new SceneSpec(number, name, cueNote, autoFollow, keyOnly, timing)));
	}

	@Override
	public Scene record(long projectId, long sceneId) {
		requireActive(projectId);
		requireScene(projectId, sceneId);
		return published(scenes.replaceValues(sceneId, programmerSnapshot()));
	}

	@Override
	public void loadIntoProgrammer(long projectId, long sceneId) {
		requireActive(projectId);
		programmer.loadValues(requireScene(projectId, sceneId).values());
	}

	@Override
	public void delete(long projectId, long sceneId) {
		requireScene(projectId, sceneId);
		scenes.delete(sceneId);
		events.publish(StateTopic.SCENES);
	}

	/** Programmer values without fixtures that have no values left. */
	private Map<Long, FixtureValues> programmerSnapshot() {
		var snapshot = new HashMap<Long, FixtureValues>();
		programmer.programmerValues().forEach((fixtureId, values) -> {
			if (!values.isEmpty()) {
				snapshot.put(fixtureId, values);
			}
		});
		return snapshot;
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
