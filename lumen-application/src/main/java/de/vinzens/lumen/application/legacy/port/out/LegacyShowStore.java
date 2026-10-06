package de.vinzens.lumen.application.legacy.port.out;

import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.domain.look.Look;

import java.time.Instant;
import java.util.List;

/** The show data a database kept from before iteration S, until it has been converted. */
public interface LegacyShowStore {

	/** Projects with old show data that has not been converted yet. */
	List<Long> pendingProjects();

	LegacyShow load(long projectId);

	/** Earlier looks of a scene, newest first. */
	List<Revision> revisions(long sceneId);

	/** The old data of the project stays where it is, but is not converted again. */
	void markConverted(long projectId, Instant at);

	record Revision(Instant createdAt, String reason, Look look) {
	}
}
