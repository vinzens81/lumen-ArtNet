package de.vinzens.lumen.application;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only compositions per scene for tests. */
public record FixedCompositions(Map<Long, List<Composition>> byScene) implements CompositionRepository {

	public static final FixedCompositions NONE = new FixedCompositions(Map.of());

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
}
