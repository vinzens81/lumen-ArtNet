package de.vinzens.lumen.application.playback.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Remembers the scene on stage per project, so the show can be resumed after a crash or restart.
 */
public interface PlaybackStateRepository {

	/** The scene that was on stage when the project was last played, if any. */
	Optional<Long> currentScene(long projectId);

	/** @param sceneId the scene on stage, empty before the first scene or after the end of the show */
	void save(long projectId, Optional<Long> sceneId, Instant now);
}
