package de.vinzens.lumen.application.legacy;

import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.scene.SceneSpec;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The scenes, compositions and extra keys of a project as they were stored before iteration S: scenes with fixed
 * values, copies of library compositions in scenes, and project-wide extra keys that play a composition or a scene.
 * Read from old databases and old export files, and turned into looks, compositions and scenes by
 * {@link LegacyConverter}.
 *
 * @param sceneKeys the slots of the extra keys usable per scene id
 */
public record LegacyShow(List<LegacyScene> scenes, List<LegacyComposition> compositions, List<LegacyKey> keys,
		Map<Long, Set<Integer>> sceneKeys) {

	public static final LegacyShow EMPTY = new LegacyShow(List.of(), List.of(), List.of(), Map.of());

	public LegacyShow {
		scenes = List.copyOf(scenes);
		compositions = List.copyOf(compositions);
		keys = List.copyOf(keys);
		sceneKeys = Map.copyOf(sceneKeys);
	}

	/** @param spec with {@code outsideCueList} set for scenes that were only played by extra keys */
	public record LegacyScene(long id, SceneSpec spec, Look look) {

		public LegacyScene {
			Objects.requireNonNull(spec, "spec");
			Objects.requireNonNull(look, "look");
		}
	}

	/**
	 * A composition in the library ({@code sceneId} empty) or a copy in a scene.
	 *
	 * @param target empty for step sequences (they run on the fixtures of their steps)
	 */
	public record LegacyComposition(long id, Optional<Long> sceneId, Optional<Long> sourceId, int position,
			String name, Pattern pattern, Optional<Target> target, double level, double speedFactor, boolean restart,
			boolean once, boolean perHead) {

		public LegacyComposition {
			Objects.requireNonNull(sceneId, "sceneId");
			Objects.requireNonNull(sourceId, "sourceId");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(pattern, "pattern");
			Objects.requireNonNull(target, "target");
		}
	}

	/** An extra key: plays its own copy of a composition, or a scene. */
	public record LegacyKey(int slot, KeyMode mode, Duration fade, Optional<LegacyComposition> composition,
			Optional<Long> sceneId) {

		public LegacyKey {
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(fade, "fade");
			Objects.requireNonNull(composition, "composition");
			Objects.requireNonNull(sceneId, "sceneId");
		}
	}
}
