package de.vinzens.lumen.application.scene.port.out;

import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneRevision;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SceneRepository {

	/** Scenes of the project, ordered by number. */
	List<Scene> findByProject(long projectId);

	Optional<Scene> findById(long id);

	Scene create(long projectId, SceneSpec spec, Map<Long, FixtureValues> values);

	Scene updateSpec(long id, SceneSpec spec);

	Scene replaceValues(long id, Map<Long, FixtureValues> values);

	void delete(long id);

	/** Saves an earlier look of a scene and drops all but the newest {@code keep} revisions of the scene. */
	SceneRevision saveRevision(long sceneId, Instant createdAt, String reason, Map<Long, FixtureValues> values,
			int keep);

	/** Revisions of a scene, newest first. */
	List<SceneRevision> findRevisions(long sceneId);

	Optional<SceneRevision> findRevision(long id);
}
