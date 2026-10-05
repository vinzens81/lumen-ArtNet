package de.vinzens.lumen.application.scene.port.in;

import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The scene list (cue list) of a project. Recording and loading work with the programmer and therefore only
 * for the active project.
 */
public interface ManageScenesUseCase {

	/** Scenes ordered by number. */
	List<Scene> scenes(long projectId);

	/**
	 * Creates a scene.
	 *
	 * @param number                 the cue number, or empty for the next whole number after the last scene
	 * @param keyOnly                only played by extra keys, GO skips it
	 * @param timing                 fade times of the GO into the scene
	 * @param script                 text spoken on stage during the scene, empty if none
	 * @param recordFromProgrammer   take the current programmer values, otherwise the scene starts empty (dark)
	 */
	Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing, String script,
			boolean recordFromProgrammer);

	Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean keyOnly, SceneTiming timing, String script);

	/**
	 * Copies a scene with its look, compositions and extra keys. Copied compositions keep their library origin, so a
	 * chase runs on seamlessly from the scene into its copy.
	 *
	 * @param number the cue number of the copy, or empty for the next whole number after the last scene
	 */
	Scene duplicate(long projectId, long sceneId, Optional<SceneNumber> number);

	/** Overwrites the values of the scene with the current programmer. */
	Scene record(long projectId, long sceneId);

	/** Replaces the programmer with the values of the scene, to preview and edit it. */
	void loadIntoProgrammer(long projectId, long sceneId);

	void delete(long projectId, long sceneId);
}
