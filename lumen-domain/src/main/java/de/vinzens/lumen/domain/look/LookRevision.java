package de.vinzens.lumen.domain.look;

import java.time.Instant;
import java.util.Objects;

/**
 * An earlier content of a look, saved before its values were replaced, so the change can be undone.
 *
 * @param reason what replaced this content, e.g. "Update from programmer"
 */
public record LookRevision(long id, long lookId, Instant createdAt, String reason, Look look) {

	/** Revisions kept per look; older ones are dropped. */
	public static final int KEEP = 20;

	public LookRevision {
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(look, "look");
	}
}
