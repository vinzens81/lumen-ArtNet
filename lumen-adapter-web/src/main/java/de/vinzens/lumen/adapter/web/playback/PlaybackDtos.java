package de.vinzens.lumen.adapter.web.playback;

import de.vinzens.lumen.application.playback.port.in.ControlPlaybackUseCase.PlaybackStatus;

import java.time.Duration;

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
	 */
	public record PlaybackResponse(boolean available, int position, int sceneCount, Long currentSceneId,
			Long nextSceneId, boolean nextIsEnd, FadeResponse fade, boolean paused, Long autoFollowInMs,
			Long resumeSceneId) {

		public static PlaybackResponse from(PlaybackStatus status) {
			return new PlaybackResponse(status.available(), status.position(), status.sceneCount(),
					status.currentSceneId().orElse(null), status.nextSceneId().orElse(null), status.nextIsEnd(),
					status.fade().map(f -> new FadeResponse(f.fromSceneId().orElse(null), f.elapsed().toMillis(),
							f.fadeIn().toMillis(), f.fadeOut().toMillis())).orElse(null),
					status.paused(), status.autoFollowIn().map(Duration::toMillis).orElse(null),
					status.resumeSceneId().orElse(null));
		}
	}

	public record FadeResponse(Long fromSceneId, long elapsedMs, long fadeInMs, long fadeOutMs) {
	}
}
