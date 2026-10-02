package de.vinzens.lumen.application.composition;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.composition.port.in.ManageExtraKeysUseCase;
import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class ExtraKeyService implements ManageExtraKeysUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final CompositionRepository compositions;
	private final StateEvents events;

	public ExtraKeyService(ProjectRepository projects, SceneRepository scenes, CompositionRepository compositions,
			StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.compositions = Objects.requireNonNull(compositions, "compositions");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<ExtraKey> keys(long projectId) {
		requireProject(projectId);
		return compositions.findKeys(projectId);
	}

	@Override
	public ExtraKey assignKey(long projectId, int slot, long libraryCompositionId, KeyMode mode, Duration fade) {
		requireProject(projectId);
		ExtraKey.validate(slot, fade);
		var source = compositions.findById(libraryCompositionId)
				.filter(c -> c.projectId() == projectId && c.inLibrary())
				.orElseThrow(() -> new NotFoundException("Composition " + libraryCompositionId
						+ " is not in the library of project " + projectId));
		var wasEmpty = compositions.findKeys(projectId).stream().noneMatch(k -> k.slot() == slot);
		var key = compositions.saveKey(projectId, slot, Optional.of(source.id()), mode, fade, source.spec());
		if (wasEmpty) {
			var sceneKeys = compositions.findSceneKeys(projectId);
			for (var scene : scenes.findByProject(projectId)) {
				var slots = new TreeSet<>(sceneKeys.getOrDefault(scene.id(), Set.of()));
				slots.add(slot);
				compositions.saveSceneKeys(scene.id(), slots);
			}
		}
		return changed(key);
	}

	@Override
	public ExtraKey updateKey(long projectId, int slot, KeyMode mode, Duration fade) {
		requireKey(projectId, slot);
		ExtraKey.validate(slot, fade);
		return changed(compositions.updateKeySettings(projectId, slot, mode, fade));
	}

	@Override
	public void clearKey(long projectId, int slot) {
		requireKey(projectId, slot);
		compositions.deleteKey(projectId, slot);
		compositions.findSceneKeys(projectId).forEach((sceneId, slots) -> {
			if (slots.contains(slot)) {
				var remaining = new HashSet<>(slots);
				remaining.remove(slot);
				compositions.saveSceneKeys(sceneId, remaining);
			}
		});
		events.publish(StateTopic.COMPOSITIONS);
		events.publish(StateTopic.SCENES);
	}

	@Override
	public Set<Integer> sceneKeys(long projectId, long sceneId) {
		requireScene(projectId, sceneId);
		return compositions.findSceneKeys(projectId).getOrDefault(sceneId, Set.of());
	}

	@Override
	public Set<Integer> setSceneKeys(long projectId, long sceneId, Set<Integer> slots) {
		requireScene(projectId, sceneId);
		var assigned = new HashSet<Integer>();
		compositions.findKeys(projectId).forEach(k -> assigned.add(k.slot()));
		var unknown = slots.stream().filter(slot -> !assigned.contains(slot)).sorted().toList();
		if (!unknown.isEmpty()) {
			throw new DomainValidationException(List.of("No composition is on extra key " + unknown));
		}
		compositions.saveSceneKeys(sceneId, Set.copyOf(slots));
		events.publish(StateTopic.SCENES);
		return sceneKeys(projectId, sceneId);
	}

	private ExtraKey changed(ExtraKey key) {
		events.publish(StateTopic.COMPOSITIONS);
		events.publish(StateTopic.SCENES);
		return key;
	}

	private void requireProject(long projectId) {
		projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}

	private void requireScene(long projectId, long sceneId) {
		scenes.findById(sceneId).filter(s -> s.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + sceneId + " does not exist in project " + projectId));
	}

	private void requireKey(long projectId, int slot) {
		requireProject(projectId);
		if (compositions.findKeys(projectId).stream().noneMatch(k -> k.slot() == slot)) {
			throw new NotFoundException("Extra key " + slot + " is empty");
		}
	}
}
