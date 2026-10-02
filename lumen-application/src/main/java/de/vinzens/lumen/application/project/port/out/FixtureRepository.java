package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.List;
import java.util.Optional;

public interface FixtureRepository {

	/** Fixtures of the project, sorted by sortOrder and name. */
	List<PatchedFixture> findByProject(long projectId);

	Optional<PatchedFixture> findById(long id);

	PatchedFixture create(long projectId, FixtureSpec spec);

	PatchedFixture update(long id, FixtureSpec spec);

	/** Deletes the fixture and removes it from all groups. */
	void delete(long id);

	int countByUniverse(long universeId);
}
