package de.vinzens.lumen.application.composition.port.in;

import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Setting up the extra keys of show mode: which composition is on which key, how the key behaves and in which
 * scenes it can be used. The composition of a key is a copy and is edited like any composition.
 */
public interface ManageExtraKeysUseCase {

	/** Assigned keys, by slot. */
	List<ExtraKey> keys(long projectId);

	/**
	 * Puts a copy of a library composition on a key. A key that was empty becomes usable in every scene; replacing
	 * the composition of a key keeps its scenes.
	 */
	ExtraKey assignKey(long projectId, int slot, long libraryCompositionId, KeyMode mode, Duration fade);

	ExtraKey updateKey(long projectId, int slot, KeyMode mode, Duration fade);

	/** Empties the key; it disappears from all scenes. */
	void clearKey(long projectId, int slot);

	/** Keys usable while the scene is on stage. */
	Set<Integer> sceneKeys(long projectId, long sceneId);

	Set<Integer> setSceneKeys(long projectId, long sceneId, Set<Integer> slots);
}
