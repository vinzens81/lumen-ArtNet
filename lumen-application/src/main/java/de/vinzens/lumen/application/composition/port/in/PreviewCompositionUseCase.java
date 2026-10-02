package de.vinzens.lumen.application.composition.port.in;

import de.vinzens.lumen.domain.output.FixtureValues;

import java.util.Map;
import java.util.Optional;

/**
 * Lets a composition run for the editor: as values for an on-screen preview, and if wanted on the real stage.
 */
public interface PreviewCompositionUseCase {

	/**
	 * Values of the composition now, per target fixture, as they would show over an empty stage (relative
	 * compositions such as flicker over full intensity). Only attributes the fixtures have.
	 */
	Map<Long, FixtureValues> previewValues(long compositionId);

	/** Plays the composition on stage above the playback (setup mode only). */
	void showOnStage(long projectId, long compositionId);

	void stopOnStage();

	Optional<Long> compositionOnStage();
}
