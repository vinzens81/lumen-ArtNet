package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase.PlaybackStatus;

import de.vinzens.lumen.domain.composition.ExtraKeys.KeyStatus;

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
	 * @param keys           assigned extra keys
	 * @param scriptLine     highlighted sentence of the script of the current scene (stage directions not counted)
	 */
	public record PlaybackResponse(boolean available, int position, int sceneCount, Long currentSceneId,
			Long nextSceneId, boolean nextIsEnd, FadeResponse fade, boolean paused, Long autoFollowInMs,
			Long resumeSceneId, List<KeyResponse> keys, int scriptLine) {

		public static PlaybackResponse from(PlaybackStatus status) {
			return new PlaybackResponse(status.available(), status.position(), status.sceneCount(),
					status.currentSceneId().orElse(null), status.nextSceneId().orElse(null), status.nextIsEnd(),
					status.fade().map(f -> new FadeResponse(f.fromSceneId().orElse(null), f.elapsed().toMillis(),
							f.fadeIn().toMillis(), f.fadeOut().toMillis(), f.delay().toMillis())).orElse(null),
					status.paused(), status.autoFollowIn().map(Duration::toMillis).orElse(null),
					status.resumeSceneId().orElse(null), status.keys().stream().map(KeyResponse::from).toList(),
					status.scriptLine());
		}
	}

	/**
	 * @param kind      kind of the composition, {@code scene} if the key plays a scene
	 * @param on        switched on (possibly still fading in)
	 * @param level     current fade level 0–1
	 * @param available can be used in the scene on stage
	 */
	public record KeyResponse(int slot, String name, String kind, String mode, boolean on, double level,
			boolean available) {

		static KeyResponse from(KeyStatus key) {
			return new KeyResponse(key.slot(), key.name(), key.kind().map(kind -> kind.id()).orElse("scene"),
					key.mode().id(), key.on(),
					Math.round(key.level() * 1000) / 1000.0, key.available());
		}
	}

	/** @param delayMs the fade-in starts this long after GO */
	public record FadeResponse(Long fromSceneId, long elapsedMs, long fadeInMs, long fadeOutMs, long delayMs) {
	}
}
