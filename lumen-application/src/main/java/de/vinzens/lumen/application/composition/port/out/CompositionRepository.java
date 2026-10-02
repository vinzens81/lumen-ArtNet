package de.vinzens.lumen.application.composition.port.out;

import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CompositionRepository {

	/** Library compositions of the project, by name. */
	List<Composition> findLibrary(long projectId);

	/** Copies in a scene, in their order. */
	List<Composition> findByScene(long sceneId);

	/** Copies in all scenes of the project: scene id → compositions in their order. */
	Map<Long, List<Composition>> findByScenes(long projectId);

	Optional<Composition> findById(long id);

	/**
	 * @param sceneId  empty = library
	 * @param sourceId library origin of a copy
	 * @return the new composition, placed after the existing ones of the scene
	 */
	Composition create(long projectId, Optional<Long> sceneId, Optional<Long> sourceId, CompositionSpec spec);

	Composition update(long id, CompositionSpec spec);

	void delete(long id);

	/** New order of the copies in a scene; {@code ids} are all copies of the scene. */
	void reorder(long sceneId, List<Long> ids);

	// --- extra keys --------------------------------------------------------------------------------------------

	/** Assigned extra keys of the project, by slot. */
	List<ExtraKey> findKeys(long projectId);

	/** Puts a composition on a key, replacing what was on it. */
	ExtraKey saveKey(long projectId, int slot, Optional<Long> sourceId, KeyMode mode, Duration fade,
			CompositionSpec spec);

	ExtraKey updateKeySettings(long projectId, int slot, KeyMode mode, Duration fade);

	void deleteKey(long projectId, int slot);

	/** Keys usable per scene of the project: scene id → slots. Scenes without keys are missing. */
	Map<Long, Set<Integer>> findSceneKeys(long projectId);

	void saveSceneKeys(long sceneId, Set<Integer> slots);
}
