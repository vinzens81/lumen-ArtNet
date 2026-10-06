package de.vinzens.lumen.application.look.port.out;

import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LookRepository {

	/** Shared looks and those of compositions, by name. */
	List<NamedLook> findByProject(long projectId);

	Optional<NamedLook> findById(long id);

	NamedLook create(long projectId, LookSpec spec, Look look);

	NamedLook updateSpec(long id, LookSpec spec);

	NamedLook replaceLook(long id, Look look);

	void delete(long id);

	/** Saves an earlier content of a look and drops all but the newest {@code keep} revisions of the look. */
	LookRevision saveRevision(long lookId, Instant createdAt, String reason, Look look, int keep);

	/** Revisions of a look, newest first. */
	List<LookRevision> findRevisions(long lookId);

	Optional<LookRevision> findRevision(long id);
}
