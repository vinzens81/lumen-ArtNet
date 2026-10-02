package de.vinzens.lumen.application.project.port.in;

import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchIssue;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;

import java.util.List;

/**
 * Universes, fixtures and groups of a project. All methods check that the objects belong to the project.
 */
public interface ManagePatchUseCase {

	List<Universe> universes(long projectId);

	Universe createUniverse(long projectId, UniverseSpec spec);

	Universe updateUniverse(long projectId, long universeId, UniverseSpec spec);

	/** Fails as long as fixtures are still patched in the universe. */
	void deleteUniverse(long projectId, long universeId);

	List<PatchedFixture> fixtures(long projectId);

	PatchedFixture createFixture(long projectId, FixtureSpec spec);

	PatchedFixture updateFixture(long projectId, long fixtureId, FixtureSpec spec);

	void deleteFixture(long projectId, long fixtureId);

	List<Group> groups(long projectId);

	Group createGroup(long projectId, GroupSpec spec);

	Group updateGroup(long projectId, long groupId, GroupSpec spec);

	void deleteGroup(long projectId, long groupId);

	/** Validates the patch against the current fixture library. */
	List<PatchIssue> issues(long projectId);
}
