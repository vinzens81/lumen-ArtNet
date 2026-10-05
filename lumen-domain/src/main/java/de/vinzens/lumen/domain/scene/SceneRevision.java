package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An earlier look of a scene, saved before its values were replaced, so the change can be undone.
 *
 * @param reason what replaced this look, e.g. "Update from programmer"
 */
public record SceneRevision(long id, long sceneId, Instant createdAt, String reason, Map<Long, FixtureValues> values) {

	/** Revisions kept per scene; older ones are dropped. */
	public static final int KEEP = 20;

	public SceneRevision {
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(reason, "reason");
		values = Map.copyOf(values);
	}
}
