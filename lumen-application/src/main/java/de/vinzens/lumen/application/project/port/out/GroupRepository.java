package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

	/** Groups of the project, sorted by sortOrder and name. */
	List<Group> findByProject(long projectId);

	Optional<Group> findById(long id);

	Group create(long projectId, GroupSpec spec);

	Group update(long id, GroupSpec spec);

	void delete(long id);
}
