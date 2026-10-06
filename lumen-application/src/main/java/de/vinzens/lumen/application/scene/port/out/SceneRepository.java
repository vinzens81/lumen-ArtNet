package de.vinzens.lumen.application.scene.port.out;

import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.util.List;
import java.util.Optional;

public interface SceneRepository {

	/** Scenes of the project, ordered by number. */
	List<Scene> findByProject(long projectId);

	Optional<Scene> findById(long id);

	Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys);

	Scene updateSpec(long id, SceneSpec spec);

	/** The compositions at the start of the scene in their new order. */
	Scene replaceCompositions(long id, List<SceneComposition> compositions);

	/**
	 * The light of a part of the scene: compositions and looks after the marker, or at the start for {@code ""}.
	 */
	Scene replaceSection(long id, String marker, List<SceneComposition> compositions, List<SceneLook> looks);

	Scene replaceKeys(long id, List<SceneKey> keys);

	void delete(long id);
}
