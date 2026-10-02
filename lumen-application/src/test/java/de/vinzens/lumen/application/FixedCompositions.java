package de.vinzens.lumen.application;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only compositions per scene and extra keys for tests. */
public record FixedCompositions(Map<Long, List<Composition>> byScene, List<ExtraKey> keys,
		Map<Long, Set<Integer>> sceneKeys) implements CompositionRepository {

	public static final FixedCompositions NONE = new FixedCompositions(Map.of());

	public FixedCompositions(Map<Long, List<Composition>> byScene) {
		this(byScene, List.of(), Map.of());
	}

	@Override
	public List<Composition> findLibrary(long projectId) {
		return List.of();
	}

	@Override
	public List<Composition> findByScene(long sceneId) {
		return byScene.getOrDefault(sceneId, List.of());
	}

	@Override
	public Map<Long, List<Composition>> findByScenes(long projectId) {
		return byScene;
	}

	@Override
	public Optional<Composition> findById(long id) {
		return byScene.values().stream().flatMap(List::stream).filter(c -> c.id() == id).findFirst();
	}

	@Override
	public Composition create(long projectId, Optional<Long> sceneId, Optional<Long> sourceId, CompositionSpec spec) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Composition update(long id, CompositionSpec spec) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete(long id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void reorder(long sceneId, List<Long> ids) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<ExtraKey> findKeys(long projectId) {
		return keys;
	}

	@Override
	public ExtraKey saveKey(long projectId, int slot, Optional<Long> sourceId, KeyMode mode, Duration fade,
			CompositionSpec spec) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ExtraKey updateKeySettings(long projectId, int slot, KeyMode mode, Duration fade) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteKey(long projectId, int slot) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Map<Long, Set<Integer>> findSceneKeys(long projectId) {
		return sceneKeys;
	}

	@Override
	public void saveSceneKeys(long sceneId, Set<Integer> slots) {
		throw new UnsupportedOperationException();
	}
}
