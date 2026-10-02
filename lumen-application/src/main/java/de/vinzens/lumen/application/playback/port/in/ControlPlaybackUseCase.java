package de.vinzens.lumen.application.playback.port.in;

import java.time.Duration;
import java.util.Optional;

/**
 * Plays the scene list of the active project.
 */
public interface ControlPlaybackUseCase {

	PlaybackStatus status();

	PlaybackStatus go();

	PlaybackStatus back();

	PlaybackStatus togglePause();

	/** Completes the running fade at once. */
	PlaybackStatus finish();

	/** Chooses the scene that the next GO starts. */
	PlaybackStatus setNext(long sceneId);

	/**
	 * @param position        {@code -1} before the first scene, {@code sceneCount} at the end of the show
	 * @param nextIsEnd       the next GO fades to black (end of the show)
	 * @param fade            the running crossfade, if any
	 * @param autoFollowIn    time until the next scene starts automatically, if the current scene auto-follows
	 */
	record PlaybackStatus(boolean available, int position, int sceneCount, Optional<Long> currentSceneId,
			Optional<Long> nextSceneId, boolean nextIsEnd, Optional<Fade> fade, boolean paused,
			Optional<Duration> autoFollowIn) {

		public static final PlaybackStatus UNAVAILABLE = new PlaybackStatus(false, -1, 0, Optional.empty(),
				Optional.empty(), false, Optional.empty(), false, Optional.empty());
	}

	/**
	 * @param fromSceneId scene fading out, empty for the dark start or a mixed look
	 * @param elapsed     time since GO, paused time excluded
	 */
	record Fade(Optional<Long> fromSceneId, Duration elapsed, Duration fadeIn, Duration fadeOut) {
	}
}
