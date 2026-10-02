package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.project.port.in.ManageProjectsUseCase;
import de.vinzens.lumen.application.project.port.out.PatchChangeListener;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProjectService implements ManageProjectsUseCase {

	private final ProjectRepository projects;
	private final Clock clock;
	private final PatchChangeListener listener;

	public ProjectService(ProjectRepository projects, Clock clock, PatchChangeListener listener) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.listener = Objects.requireNonNull(listener, "listener");
	}

	@Override
	public List<Project> projects() {
		return projects.findAll();
	}

	@Override
	public Project project(long id) {
		return projects.findById(id).orElseThrow(() -> notFound(id));
	}

	@Override
	public Optional<Project> activeProject() {
		return projects.findAll().stream().filter(Project::active).findFirst();
	}

	@Override
	public Project create(ProjectSpec spec) {
		var created = projects.create(spec, clock.instant());
		if (activeProject().isEmpty()) {
			return activate(created.id());
		}
		return created;
	}

	@Override
	public Project update(long id, ProjectSpec spec) {
		project(id);
		return projects.update(id, spec, clock.instant());
	}

	@Override
	public void delete(long id) {
		project(id);
		projects.delete(id);
		listener.patchChanged();
	}

	@Override
	public Project activate(long id) {
		project(id);
		projects.activate(id);
		listener.patchChanged();
		return project(id);
	}

	static NotFoundException notFound(long id) {
		return new NotFoundException("Project " + id + " does not exist");
	}
}
