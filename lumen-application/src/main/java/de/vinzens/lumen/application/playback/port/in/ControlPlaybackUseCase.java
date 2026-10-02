package de.vinzens.lumen.application.playback.port.in;

import de.vinzens.lumen.domain.composition.ExtraKeys.KeyStatus;

import java.time.Duration;
import java.util.List;
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

	/** Puts the scene that was on stage before the restart back on stage at once (see {@code resumeSceneId}). */
	PlaybackStatus resume();

	/** Starts from the beginning instead of resuming. */
	PlaybackStatus dismissResume();

	/** Extra key pressed: a toggle switches on or off, a flash key switches on. */
	PlaybackStatus pressKey(int slot);

	/** Extra key released: switches a flash key off. */
	PlaybackStatus releaseKey(int slot);

	/** Chooses the scene that the next GO starts. */
	PlaybackStatus setNext(long sceneId);

	/**
	 * @param position        {@code -1} before the first scene, {@code sceneCount} at the end of the show
	 * @param nextIsEnd       the next GO fades to black (end of the show)
	 * @param fade            the running crossfade, if any
	 * @param autoFollowIn    time until the next scene starts automatically, if the current scene auto-follows
	 * @param resumeSceneId   scene that was on stage before a restart; offered until resumed, dismissed or GO
	 * @param keys            assigned extra keys with their state
	 */
	record PlaybackStatus(boolean available, int position, int sceneCount, Optional<Long> currentSceneId,
			Optional<Long> nextSceneId, boolean nextIsEnd, Optional<Fade> fade, boolean paused,
			Optional<Duration> autoFollowIn, Optional<Long> resumeSceneId, List<KeyStatus> keys) {

		public static final PlaybackStatus UNAVAILABLE = new PlaybackStatus(false, -1, 0, Optional.empty(),
				Optional.empty(), false, Optional.empty(), false, Optional.empty(), Optional.empty(), List.of());

		public PlaybackStatus {
			keys = List.copyOf(keys);
		}
	}

	/**
	 * @param fromSceneId scene fading out, empty for the dark start or a mixed look
	 * @param elapsed     time since GO, paused time excluded
	 */
	record Fade(Optional<Long> fromSceneId, Duration elapsed, Duration fadeIn, Duration fadeOut) {
	}
}
