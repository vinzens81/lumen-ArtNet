package de.vinzens.lumen.application.preset;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.output.port.in.ControlProgrammerUseCase;
import de.vinzens.lumen.application.preset.port.in.ManagePresetsUseCase;
import de.vinzens.lumen.application.preset.port.out.PresetRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PresetService implements ManagePresetsUseCase {

	private final ProjectRepository projects;
	private final PresetRepository presets;
	private final SceneRepository scenes;
	private final ControlProgrammerUseCase programmer;
	private final StateEvents events;

	public PresetService(ProjectRepository projects, PresetRepository presets, SceneRepository scenes,
			ControlProgrammerUseCase programmer, StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.presets = Objects.requireNonNull(presets, "presets");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.programmer = Objects.requireNonNull(programmer, "programmer");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Preset> presets(long projectId) {
		requireProject(projectId);
		return presets.findByProject(projectId);
	}

	@Override
	public Preset create(long projectId, String name, AttributeGroup group, Collection<Long> fixtureIds) {
		requireActive(projectId);
		var spec = new PresetSpec(name, group);
		if (presets.findByProject(projectId).size() >= Preset.MAX_PER_PROJECT) {
			throw new ConflictException("A project has at most " + Preset.MAX_PER_PROJECT + " presets");
		}
		var values = programmerValues(spec, fixtureIds);
		var created = presets.create(projectId, spec, values);
		programmer.applyPreset(values.keySet(), created);
		events.publish(StateTopic.PRESETS);
		return created;
	}

	@Override
	public Preset record(long projectId, long presetId, Collection<Long> fixtureIds) {
		requireActive(projectId);
		var preset = requirePreset(projectId, presetId);
		var values = new HashMap<>(preset.values());
		values.putAll(programmerValues(preset.spec(), fixtureIds));
		var updated = presets.replaceValues(presetId, values);
		programmer.applyPreset(fixtureIds.stream().filter(values::containsKey).toList(), updated);
		propagate(projectId, updated);
		return updated;
	}

	@Override
	public Preset rename(long projectId, long presetId, String name) {
		var preset = requirePreset(projectId, presetId);
		var renamed = presets.updateSpec(presetId, new PresetSpec(name, preset.spec().group()));
		events.publish(StateTopic.PRESETS);
		return renamed;
	}

	@Override
	public void apply(long projectId, long presetId, Collection<Long> fixtureIds) {
		requireActive(projectId);
		programmer.applyPreset(fixtureIds, requirePreset(projectId, presetId));
	}

	@Override
	public void delete(long projectId, long presetId) {
		requirePreset(projectId, presetId);
		presets.delete(presetId);
		programmer.presetDeleted(presetId);
		events.publish(StateTopic.PRESETS);
		events.publish(StateTopic.SCENES);
	}

	/** Every scene that refers to the preset and the programmer take its new values. */
	private void propagate(long projectId, Preset preset) {
		for (var scene : scenes.findByProject(projectId)) {
			var look = scene.look();
			if (look.uses(preset.id())) {
				var updated = look.withPreset(preset);
				if (!updated.equals(look)) {
					scenes.replaceLook(scene.id(), updated);
				}
			}
		}
		programmer.presetChanged(preset);
		events.publish(StateTopic.PRESETS);
		events.publish(StateTopic.SCENES);
	}

	/** Programmer values of the given fixtures for the preset's group; an error if there are none. */
	private Map<Long, FixtureValues> programmerValues(PresetSpec spec, Collection<Long> fixtureIds) {
		var selected = Set.copyOf(fixtureIds);
		var fromProgrammer = new HashMap<>(programmer.programmerValues());
		fromProgrammer.keySet().retainAll(selected);
		var values = Preset.valuesOf(spec, fromProgrammer);
		if (values.isEmpty()) {
			throw new DomainValidationException(List.of("The selected fixtures have no " + spec.group().id()
					+ " values in the programmer – set some first"));
		}
		return values;
	}

	private Preset requirePreset(long projectId, long presetId) {
		requireProject(projectId);
		return presets.findById(presetId)
				.filter(preset -> preset.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Preset " + presetId + " does not exist in project "
						+ projectId));
	}

	private void requireProject(long projectId) {
		projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}

	private void requireActive(long projectId) {
		var project = projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
		if (!project.active()) {
			throw new ConflictException("The programmer belongs to the active project – activate project "
					+ projectId + " first");
		}
	}
}
