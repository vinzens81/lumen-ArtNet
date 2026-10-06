package de.vinzens.lumen.application.scene.port.out;

import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.util.List;
import java.util.Optional;

public interface SceneRepository {

	/** Scenes of the project, ordered by number. */
	List<Scene> findByProject(long projectId);

	Optional<Scene> findById(long id);

	Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys);

	Scene updateSpec(long id, SceneSpec spec);

	/** The compositions of the scene in their new order. */
	Scene replaceCompositions(long id, List<SceneComposition> compositions);

	Scene replaceKeys(long id, List<SceneKey> keys);

	void delete(long id);
}
