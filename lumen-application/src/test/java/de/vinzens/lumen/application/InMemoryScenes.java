package de.vinzens.lumen.application;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneSection;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Scenes in memory, for tests. */
public class InMemoryScenes implements SceneRepository {

	private final Map<Long, Scene> scenes = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public List<Scene> findByProject(long projectId) {
		return scenes.values().stream().filter(s -> s.projectId() == projectId)
				.sorted(Comparator.comparing(s -> s.spec().number())).toList();
	}

	@Override
	public Optional<Scene> findById(long id) {
		return Optional.ofNullable(scenes.get(id));
	}

	@Override
	public Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys) {
		return put(new Scene(nextId++, projectId, spec, compositions, keys));
	}

	@Override
	public Scene updateSpec(long id, SceneSpec spec) {
		var scene = scenes.get(id);
		return put(new Scene(id, scene.projectId(), spec, scene.compositions(), scene.keys(), scene.looks(),
				scene.sections()));
	}

	@Override
	public Scene replaceCompositions(long id, List<SceneComposition> compositions) {
		var scene = scenes.get(id);
		return put(scene.withStart(compositions, scene.looks()));
	}

	@Override
	public Scene replaceSection(long id, String marker, List<SceneComposition> compositions, List<SceneLook> looks) {
		var scene = scenes.get(id);
		return put(marker.isEmpty() ? scene.withStart(compositions, looks)
				: scene.withSection(new SceneSection(marker, compositions, looks)));
	}

	@Override
	public Scene replaceKeys(long id, List<SceneKey> keys) {
		var scene = scenes.get(id);
		return put(new Scene(id, scene.projectId(), scene.spec(), scene.compositions(), keys, scene.looks(),
				scene.sections()));
	}

	@Override
	public void delete(long id) {
		scenes.remove(id);
	}

	private Scene put(Scene scene) {
		scenes.put(scene.id(), scene);
		return scene;
	}
}
