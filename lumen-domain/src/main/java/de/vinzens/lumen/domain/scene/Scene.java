package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The third level of the show: a set of compositions that run side by side from GO, and the extra keys usable while
 * the scene is on stage. Fixtures in none of its compositions are dark in it.
 *
 * @param compositions in order: on the same attribute a later one wins (intensity: the highest)
 */
public record Scene(long id, long projectId, SceneSpec spec, List<SceneComposition> compositions,
		List<SceneKey> keys) {

	public Scene {
		Objects.requireNonNull(spec, "spec");
		compositions = List.copyOf(compositions);
		keys = List.copyOf(keys);
		var compositionIds = new HashSet<Long>();
		var slots = new HashSet<Integer>();
		new Validation()
				.require(compositions.stream().allMatch(c -> compositionIds.add(c.compositionId())),
						"a composition is in the scene twice")
				.require(keys.stream().allMatch(k -> slots.add(k.slot())), "a key is used twice")
				.throwIfInvalid();
	}

	/** A scene without compositions and keys yet. */
	public Scene(long id, long projectId, SceneSpec spec) {
		this(id, projectId, spec, List.of(), List.of());
	}

	public boolean contains(long compositionId) {
		return compositions.stream().anyMatch(c -> c.compositionId() == compositionId);
	}
}
