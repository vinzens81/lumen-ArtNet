package de.vinzens.lumen.application.playback;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.events.StateEvents;
import de.vinzens.lumen.application.events.StateTopic;
import de.vinzens.lumen.application.playback.port.in.ManageFadersUseCase;
import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.Fader;

import java.util.List;
import java.util.Objects;

public class FaderService implements ManageFadersUseCase {

	private final ProjectRepository projects;
	private final SceneRepository scenes;
	private final FaderRepository faders;
	private final StateEvents events;

	public FaderService(ProjectRepository projects, SceneRepository scenes, FaderRepository faders,
			StateEvents events) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.scenes = Objects.requireNonNull(scenes, "scenes");
		this.faders = Objects.requireNonNull(faders, "faders");
		this.events = Objects.requireNonNull(events, "events");
	}

	@Override
	public List<Fader> faders(long projectId) {
		requireProject(projectId);
		return faders.findByProject(projectId);
	}

	@Override
	public Fader save(long projectId, Fader fader) {
		requireProject(projectId);
		scenes.findById(fader.sceneId())
				.filter(scene -> scene.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Scene " + fader.sceneId() + " does not exist in project "
						+ projectId));
		var saved = faders.save(projectId, fader);
		events.publish(StateTopic.FADERS);
		return saved;
	}

	@Override
	public void delete(long projectId, int slot) {
		requireProject(projectId);
		faders.delete(projectId, slot);
		events.publish(StateTopic.FADERS);
	}

	private void requireProject(long projectId) {
		projects.findById(projectId)
				.orElseThrow(() -> new NotFoundException("Project " + projectId + " does not exist"));
	}
}
