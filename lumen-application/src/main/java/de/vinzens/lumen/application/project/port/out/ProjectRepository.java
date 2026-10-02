package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

	List<Project> findAll();

	Optional<Project> findById(long id);

	Project create(ProjectSpec spec, Instant now);

	Project update(long id, ProjectSpec spec, Instant now);

	/** Deletes the project including universes, patch and groups. */
	void delete(long id);

	/** Makes the project the only active project. */
	void activate(long id);
}
