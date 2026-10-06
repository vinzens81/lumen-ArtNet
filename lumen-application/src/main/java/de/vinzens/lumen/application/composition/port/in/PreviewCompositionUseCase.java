package de.vinzens.lumen.application.composition.port.in;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.playback.Playback.FadeStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Lets a composition run for the editor: as values for an on-screen preview, and if wanted on the real stage –
 * alone or as part of its scene.
 */
public interface PreviewCompositionUseCase {

	/**
	 * Values of the composition now, per target fixture, as they would show over an empty stage (relative
	 * compositions such as flicker over full intensity). Only attributes the fixtures have.
	 */
	Map<Long, FixtureValues> previewValues(long compositionId);

	/** Plays the composition on stage above the playback (setup mode only). */
	void showOnStage(long projectId, long compositionId);

	/** Plays a whole scene – its compositions – on stage above the playback (setup mode only). */
	void showSceneOnStage(long projectId, long sceneId);

	/**
	 * Rehearses the GO into a scene on stage (setup mode only): the scene before it in the cue list – or the dark
	 * stage for the first scene – fades into it with the fade times of the show, then the scene stays on stage.
	 */
	void rehearseFade(long projectId, long sceneId);

	void stopOnStage();

	Optional<Long> compositionOnStage();

	/** The scene on stage, shown directly or as the target of a rehearsed fade. */
	Optional<Long> sceneOnStage();

	/** The running rehearsed fade, empty once it has ended. */
	Optional<FadeStatus> fadeOnStage();
}
