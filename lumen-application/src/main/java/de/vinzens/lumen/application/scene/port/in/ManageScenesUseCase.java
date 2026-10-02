package de.vinzens.lumen.application.scene.port.in;

import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;

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
	 * @param recordFromProgrammer   take the current programmer values, otherwise the scene starts empty (dark)
	 */
	Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, boolean recordFromProgrammer);

	Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow);

	/** Overwrites the values of the scene with the current programmer. */
	Scene record(long projectId, long sceneId);

	/** Replaces the programmer with the values of the scene, to preview and edit it. */
	void loadIntoProgrammer(long projectId, long sceneId);

	void delete(long projectId, long sceneId);
}
