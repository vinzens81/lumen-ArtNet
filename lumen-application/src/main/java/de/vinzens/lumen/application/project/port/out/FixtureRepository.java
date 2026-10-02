package de.vinzens.lumen.application.project.port.out;

import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;

import java.util.List;
import java.util.Optional;

public interface FixtureRepository {

	/** Geräte des Projekts, sortiert nach sortOrder und Name. */
	List<PatchedFixture> findByProject(long projectId);

	Optional<PatchedFixture> findById(long id);

	PatchedFixture create(long projectId, FixtureSpec spec);

	PatchedFixture update(long id, FixtureSpec spec);

	/** Löscht das Gerät und entfernt es aus allen Gruppen. */
	void delete(long id);

	int countByUniverse(long universeId);
}
