package de.vinzens.lumen.application.project;

import de.vinzens.lumen.application.ConflictException;
import de.vinzens.lumen.application.NotFoundException;
import de.vinzens.lumen.application.library.port.in.QueryFixtureLibraryUseCase;
import de.vinzens.lumen.application.project.port.in.ManagePatchUseCase;
import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import de.vinzens.lumen.domain.project.PatchIssue;
import de.vinzens.lumen.domain.project.PatchValidator;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PatchService implements ManagePatchUseCase {

	private final ProjectRepository projects;
	private final UniverseRepository universes;
	private final FixtureRepository fixtures;
	private final GroupRepository groups;
	private final QueryFixtureLibraryUseCase library;

	public PatchService(ProjectRepository projects, UniverseRepository universes, FixtureRepository fixtures,
			GroupRepository groups, QueryFixtureLibraryUseCase library) {
		this.projects = Objects.requireNonNull(projects, "projects");
		this.universes = Objects.requireNonNull(universes, "universes");
		this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.library = Objects.requireNonNull(library, "library");
	}

	// --- Universen -------------------------------------------------------------------------------------------

	@Override
	public List<Universe> universes(long projectId) {
		requireProject(projectId);
		return universes.findByProject(projectId);
	}

	@Override
	public Universe createUniverse(long projectId, UniverseSpec spec) {
		requireProject(projectId);
		requireUniqueNumber(projectId, spec.number(), null);
		return universes.create(projectId, spec);
	}

	@Override
	public Universe updateUniverse(long projectId, long universeId, UniverseSpec spec) {
		requireUniverse(projectId, universeId);
		requireUniqueNumber(projectId, spec.number(), universeId);
		return universes.update(universeId, spec);
	}

	@Override
	public void deleteUniverse(long projectId, long universeId) {
		var universe = requireUniverse(projectId, universeId);
		var inUse = fixtures.countByUniverse(universeId);
		if (inUse > 0) {
			throw new ConflictException(universe.displayName() + " wird noch von " + inUse
					+ (inUse == 1 ? " Gerät" : " Geräten") + " verwendet");
		}
		universes.delete(universeId);
	}

	private void requireUniqueNumber(long projectId, int number, Long ownId) {
		var taken = universes.findByProject(projectId).stream()
				.anyMatch(u -> u.spec().number() == number && !Objects.equals(u.id(), ownId));
		if (taken) {
			throw new ConflictException("Universe " + number + " existiert in diesem Projekt bereits");
		}
	}

	private Universe requireUniverse(long projectId, long universeId) {
		return universes.findById(universeId)
				.filter(u -> u.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Universe " + universeId + " existiert in Projekt "
						+ projectId + " nicht"));
	}

	// --- Geräte ----------------------------------------------------------------------------------------------

	@Override
	public List<PatchedFixture> fixtures(long projectId) {
		requireProject(projectId);
		return fixtures.findByProject(projectId);
	}

	@Override
	public PatchedFixture createFixture(long projectId, FixtureSpec spec) {
		requireProject(projectId);
		requireUniverseReference(projectId, spec);
		return fixtures.create(projectId, spec);
	}

	@Override
	public PatchedFixture updateFixture(long projectId, long fixtureId, FixtureSpec spec) {
		requireFixture(projectId, fixtureId);
		requireUniverseReference(projectId, spec);
		return fixtures.update(fixtureId, spec);
	}

	@Override
	public void deleteFixture(long projectId, long fixtureId) {
		requireFixture(projectId, fixtureId);
		fixtures.delete(fixtureId);
	}

	/** Ein Gerät muss auf ein Universe desselben Projekts zeigen; Typ/Modus dürfen (vorübergehend) fehlen. */
	private void requireUniverseReference(long projectId, FixtureSpec spec) {
		var exists = universes.findById(spec.universeId()).filter(u -> u.projectId() == projectId).isPresent();
		if (!exists) {
			throw new DomainValidationException(List.of("universeId " + spec.universeId()
					+ " existiert in diesem Projekt nicht"));
		}
	}

	private PatchedFixture requireFixture(long projectId, long fixtureId) {
		return fixtures.findById(fixtureId)
				.filter(f -> f.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Gerät " + fixtureId + " existiert in Projekt "
						+ projectId + " nicht"));
	}

	// --- Gruppen ---------------------------------------------------------------------------------------------

	@Override
	public List<Group> groups(long projectId) {
		requireProject(projectId);
		return groups.findByProject(projectId);
	}

	@Override
	public Group createGroup(long projectId, GroupSpec spec) {
		requireProject(projectId);
		requireMembersInProject(projectId, spec);
		return groups.create(projectId, spec);
	}

	@Override
	public Group updateGroup(long projectId, long groupId, GroupSpec spec) {
		requireGroup(projectId, groupId);
		requireMembersInProject(projectId, spec);
		return groups.update(groupId, spec);
	}

	@Override
	public void deleteGroup(long projectId, long groupId) {
		requireGroup(projectId, groupId);
		groups.delete(groupId);
	}

	private void requireMembersInProject(long projectId, GroupSpec spec) {
		var known = fixtures.findByProject(projectId).stream().map(PatchedFixture::id).collect(Collectors.toSet());
		var unknown = spec.fixtureIds().stream().filter(id -> !known.contains(id)).toList();
		if (!unknown.isEmpty()) {
			throw new DomainValidationException(List.of("Geräte " + unknown + " existieren in diesem Projekt nicht"));
		}
	}

	private Group requireGroup(long projectId, long groupId) {
		return groups.findById(groupId)
				.filter(g -> g.projectId() == projectId)
				.orElseThrow(() -> new NotFoundException("Gruppe " + groupId + " existiert in Projekt "
						+ projectId + " nicht"));
	}

	// --- Prüfung ---------------------------------------------------------------------------------------------

	@Override
	public List<PatchIssue> issues(long projectId) {
		requireProject(projectId);
		return PatchValidator.validate(universes.findByProject(projectId), fixtures.findByProject(projectId),
				library::fixtureType);
	}

	private void requireProject(long projectId) {
		if (projects.findById(projectId).isEmpty()) {
			throw ProjectService.notFound(projectId);
		}
	}
}
