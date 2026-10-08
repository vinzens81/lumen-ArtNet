package de.vinzens.lumen.application.playback.port.in;

import de.vinzens.lumen.domain.composition.ExtraKeys.KeyStatus;
import de.vinzens.lumen.domain.composition.Faders.FaderStatus;

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

	/** Sets the level (0–1) of a fader, e.g. the house lights; works in setup and show mode. */
	PlaybackStatus setFader(int slot, double level);

	/** Chooses the scene that the next GO starts. */
	PlaybackStatus setNext(long sceneId);

	/**
	 * Highlights a sentence of the script of the scene on stage, so all clients follow the same text. Ignored if the
	 * scene is no longer on stage – e.g. a step that arrives just after GO.
	 *
	 * @param line    index of the sentence among the spoken sentences of the scene (stage directions are not counted)
	 * @param section the part of the scene the sentence is in (0 = the start, n = after the n-th light marker); a new
	 *                part fades to its light. Empty = the light stays as it is
	 */
	PlaybackStatus setScriptLine(long sceneId, int line, Optional<Integer> section);

	/** Highlights a sentence without changing the light. */
	default PlaybackStatus setScriptLine(long sceneId, int line) {
		return setScriptLine(sceneId, line, Optional.empty());
	}

	/**
	 * @param position        {@code -1} before the first scene, {@code sceneCount} at the end of the show
	 * @param nextIsEnd       the next GO fades to black (end of the show)
	 * @param fade            the running crossfade, if any
	 * @param autoFollowIn    time until the next scene starts automatically, if the current scene auto-follows
	 * @param resumeSceneId   scene that was on stage before a restart; offered until resumed, dismissed or GO
	 * @param keys            assigned extra keys with their state
	 * @param scriptLine      highlighted sentence of the script of the current scene, 0 after every scene change
	 * @param section         part of the current scene whose light is on stage: 0 = the start, n = after the n-th
	 *                        light marker of the script
	 * @param faders          assigned faders with their level
	 */
	record PlaybackStatus(boolean available, int position, int sceneCount, Optional<Long> currentSceneId,
			Optional<Long> nextSceneId, boolean nextIsEnd, Optional<Fade> fade, boolean paused,
			Optional<Duration> autoFollowIn, Optional<Long> resumeSceneId, List<KeyStatus> keys, int scriptLine,
			int section, List<FaderStatus> faders) {

		public static final PlaybackStatus UNAVAILABLE = new PlaybackStatus(false, -1, 0, Optional.empty(),
				Optional.empty(), false, Optional.empty(), false, Optional.empty(), Optional.empty(), List.of(), 0,
				0, List.of());

		public PlaybackStatus {
			keys = List.copyOf(keys);
			faders = List.copyOf(faders);
		}
	}

	/**
	 * @param fromSceneId scene fading out, empty for the dark start or a mixed look
	 * @param elapsed     time since GO, paused time excluded
	 * @param delay       the fade-in starts this long after GO
	 * @param withinScene to another part of the scene on stage (a light marker), not to another scene
	 */
	record Fade(Optional<Long> fromSceneId, Duration elapsed, Duration fadeIn, Duration fadeOut, Duration delay,
			boolean withinScene) {
	}
}
