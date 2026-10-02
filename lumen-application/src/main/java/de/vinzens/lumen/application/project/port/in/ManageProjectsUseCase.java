package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;

import java.util.List;
import java.util.Optional;

public interface ManageProjectsUseCase {

	List<Project> projects();

	Project project(long id);

	Optional<Project> activeProject();

	/** Legt ein Projekt an. Gibt es noch kein aktives Projekt, wird das neue aktiv. */
	Project create(ProjectSpec spec);

	Project update(long id, ProjectSpec spec);

	void delete(long id);

	Project activate(long id);
}
