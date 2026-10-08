package de.vinzens.lumen.application.scene.port.in;

import de.vinzens.lumen.application.look.port.in.ManageLooksUseCase.RecordOptions;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneTiming;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The scene list (cue list) of a project. A scene is a set of compositions plus the extra keys usable while it is on
 * stage.
 */
public interface ManageScenesUseCase {

	/** Scenes ordered by number. */
	List<Scene> scenes(long projectId);

	/**
	 * Creates a scene. It takes over the keys of the scene before it in the cue list.
	 *
	 * @param number               the cue number, or empty for the next whole number after the last scene
	 * @param timing               fade times of the GO into the scene
	 * @param script               text spoken on stage during the scene, empty if none
	 * @param recordFromProgrammer the quick way: the programmer becomes a look and a composition with one step,
	 *                             which is the scene's first composition (active project only)
	 */
	Scene create(long projectId, Optional<SceneNumber> number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, SceneTiming timing, String script,
			boolean recordFromProgrammer);

	Scene update(long projectId, long sceneId, SceneNumber number, String name, Optional<String> cueNote,
			Optional<Duration> autoFollow, SceneTiming timing, String script);

	/**
	 * Copies a scene: the copy uses the same compositions (shared, so they continue seamlessly) and the same keys.
	 *
	 * @param number the cue number of the copy, or empty for the next whole number after the last scene
	 */
	Scene duplicate(long projectId, long sceneId, Optional<SceneNumber> number);

	/** The compositions of the scene in their order, with level, speed and restart per use. */
	Scene setCompositions(long projectId, long sceneId, List<SceneComposition> compositions);

	/**
	 * The light of a part of the scene: after the light marker {@code marker} of the script, or the start of the
	 * scene for an empty marker. Compositions, looks and moves must belong to the project.
	 */
	Scene setSection(long projectId, long sceneId, String marker, List<SceneComposition> compositions,
			List<SceneLook> looks, List<SceneMove> moves);

	/** The extra keys of the scene; each plays a composition or a move of the project. */
	Scene setKeys(long projectId, long sceneId, List<SceneKey> keys);

	/**
	 * The quick way to change a simple scene: takes the programmer into the look of its first composition if that
	 * has one step and plays a look of its own or a shared one. A scene without compositions gets a new one, as with
	 * {@link #create}. Active project only.
	 */
	Scene record(long projectId, long sceneId, RecordOptions options);

	/** Replaces the programmer with the scene as it shows right after GO (every composition at its start). */
	void loadIntoProgrammer(long projectId, long sceneId);

	/** Deletes the scene; its compositions stay. */
	void delete(long projectId, long sceneId);
}
