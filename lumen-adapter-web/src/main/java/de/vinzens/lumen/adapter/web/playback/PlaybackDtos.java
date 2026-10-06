package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase.PlaybackStatus;

import de.vinzens.lumen.domain.composition.ExtraKeys.KeyStatus;
import de.vinzens.lumen.domain.composition.Faders.FaderStatus;

import java.time.Duration;
import java.util.List;

/**
 * JSON form of the playback status. Times are milliseconds; clients interpolate fade progress and the
 * auto-follow countdown locally from the moment they received the status.
 */
public final class PlaybackDtos {

	private PlaybackDtos() {
	}

	/**
	 * @param position     -1 before the first scene, sceneCount at the end of the show
	 * @param autoFollowInMs time until the next scene starts automatically, null without auto-follow
	 * @param resumeSceneId  scene that was on stage before a restart and can be resumed, otherwise null
	 * @param keys           extra keys of the scene on stage
	 * @param scriptLine     highlighted sentence of the script of the current scene (stage directions not counted)
	 * @param section        part of the current scene on stage: 0 = start, n = after the n-th light marker
	 * @param faders         assigned faders with their level
	 */
	public record PlaybackResponse(boolean available, int position, int sceneCount, Long currentSceneId,
			Long nextSceneId, boolean nextIsEnd, FadeResponse fade, boolean paused, Long autoFollowInMs,
			Long resumeSceneId, List<KeyResponse> keys, int scriptLine, int section, List<FaderResponse> faders) {

		public static PlaybackResponse from(PlaybackStatus status) {
			return new PlaybackResponse(status.available(), status.position(), status.sceneCount(),
					status.currentSceneId().orElse(null), status.nextSceneId().orElse(null), status.nextIsEnd(),
					status.fade().map(f -> new FadeResponse(f.fromSceneId().orElse(null), f.elapsed().toMillis(),
							f.fadeIn().toMillis(), f.fadeOut().toMillis(), f.delay().toMillis())).orElse(null),
					status.paused(), status.autoFollowIn().map(Duration::toMillis).orElse(null),
					status.resumeSceneId().orElse(null), status.keys().stream().map(KeyResponse::from).toList(),
					status.scriptLine(), status.section(), status.faders().stream().map(FaderResponse::from).toList());
		}
	}

	/**
	 * @param kind      kind of the composition, {@code scene} if the key plays a scene
	 * @param on        switched on (possibly still fading in)
	 * @param level     current fade level 0–1
	 * @param available can be used in the scene on stage
	 */
	/** A key of the scene on stage; {@code name} is the name of its composition. */
	public record KeyResponse(int slot, String name, String mode, boolean on, double level) {

		static KeyResponse from(KeyStatus key) {
			return new KeyResponse(key.slot(), key.name(), key.mode().id(), key.on(),
					Math.round(key.level() * 1000) / 1000.0);
		}
	}

	/** @param level 0–1 */
	public record FaderResponse(int slot, String name, double level) {

		static FaderResponse from(FaderStatus fader) {
			return new FaderResponse(fader.slot(), fader.name(), fader.level());
		}
	}

	/** @param delayMs the fade-in starts this long after GO */
	public record FadeResponse(Long fromSceneId, long elapsedMs, long fadeInMs, long fadeOutMs, long delayMs) {
	}
}
