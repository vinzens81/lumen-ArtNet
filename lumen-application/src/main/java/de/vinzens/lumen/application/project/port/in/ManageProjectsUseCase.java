package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;

import java.util.List;
import java.util.Optional;

public interface ManageProjectsUseCase {

	List<Project> projects();

	Project project(long id);

	Optional<Project> activeProject();

	/** Creates a project. If there is no active project yet, the new one becomes active. */
	Project create(ProjectSpec spec);

	Project update(long id, ProjectSpec spec);

	void delete(long id);

	Project activate(long id);
}
