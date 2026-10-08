package de.vinzens.lumen.application.look.port.in;

import de.vinzens.lumen.domain.DomainValidationException;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.NamedLook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Looks of a project: fixed values recorded from the programmer – everything except pan and tilt, which come only
 * from moves. Recording and loading work with the programmer and therefore only for the active project.
 */
public interface ManageLooksUseCase {

	/** Shared looks and those of compositions, by name. */
	List<NamedLook> looks(long projectId);

	NamedLook look(long projectId, long lookId);

	/**
	 * Creates a shared look.
	 *
	 * @param recordFromProgrammer take the current programmer values, otherwise the look starts empty (dark)
	 */
	NamedLook create(long projectId, String name, boolean recordFromProgrammer);

	/** Renames a look; {@code shared} makes a look of a composition a shared one (not the other way round). */
	NamedLook update(long projectId, long lookId, String name, boolean shared);

	/** A shared copy of the look. */
	NamedLook duplicate(long projectId, long lookId);

	/** Overwrites the values of the look with the current programmer. */
	default NamedLook record(long projectId, long lookId) {
		return record(projectId, lookId, RecordOptions.REPLACE);
	}

	/** Takes the current programmer into the look. The content before is kept as a revision. */
	NamedLook record(long projectId, long lookId, RecordOptions options);

	/** Earlier contents of the look, newest first. */
	List<LookRevision> revisions(long projectId, long lookId);

	/** Puts an earlier content back; the current one becomes a revision, so the restore can be undone as well. */
	NamedLook restore(long projectId, long lookId, long revisionId);

	/** Replaces the programmer with the values of the look, to preview and edit it. */
	void loadIntoProgrammer(long projectId, long lookId);

	/** Deletes a look that no composition plays; otherwise a conflict names the compositions. */
	void delete(long projectId, long lookId);

	/**
	 * What {@link #record(long, long, RecordOptions)} takes from the programmer.
	 *
	 * @param merge      {@code false}: the programmer replaces the whole look; {@code true}: only the attributes
	 *                   the programmer sets are taken, everything else in the look stays
	 * @param fixtureIds merge only these fixtures, empty = all fixtures in the programmer
	 * @param groups     merge only attributes of these groups, empty = all
	 */
	record RecordOptions(boolean merge, Optional<Set<Long>> fixtureIds, Set<AttributeGroup> groups) {

		public static final RecordOptions REPLACE = new RecordOptions(false, Optional.empty(), Set.of());

		public RecordOptions {
			Objects.requireNonNull(fixtureIds, "fixtureIds");
			groups = Set.copyOf(groups);
			fixtureIds = fixtureIds.map(Set::copyOf);
			if (!merge && (fixtureIds.isPresent() || !groups.isEmpty())) {
				throw new DomainValidationException(List.of(
						"fixtureIds and groups only apply when merging – replacing takes the whole programmer"));
			}
		}
	}
}
