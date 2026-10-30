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

	/** Plays a move on stage above the playback (setup mode only): its heads follow the figure. */
	void showMoveOnStage(long projectId, long moveId);

	/**
	 * Plays the compositions of a scene on stage above the playback (setup mode only).
	 *
	 * @param marker a light marker of the saved script to play the part after it, empty for the start of the scene
	 */
	void showSceneOnStage(long projectId, long sceneId, Optional<String> marker);

	/**
	 * Fades on stage from the light there – whatever the stage preview shows, or the dark stage – into a scene
	 * (setup mode only), which stays on stage afterwards: into its start with the fade times of the GO into it, into a
	 * part after a light marker with those of passing the marker. What is in both keeps running and does not move.
	 *
	 * @param marker a light marker of the saved script, empty for the start of the scene
	 */
	void fadeIn(long projectId, long sceneId, Optional<String> marker);

	void stopOnStage();

	Optional<Long> compositionOnStage();

	Optional<Long> moveOnStage();

	/** The scene on stage, shown directly or as the target of a fade. */
	Optional<Long> sceneOnStage();

	/** The light marker of the part of {@link #sceneOnStage} shown, empty for its start. */
	Optional<String> markerOnStage();

	/** The running fade into the scene on stage, empty once it has ended. */
	Optional<FadeStatus> fadeOnStage();
}
