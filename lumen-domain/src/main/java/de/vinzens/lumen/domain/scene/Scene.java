package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The third level of the show: a set of compositions (and looks) that run side by side from GO, and the extra keys
 * usable while the scene is on stage. Fixtures in none of them are dark in it. Light markers in the script split the
 * scene into parts with light of their own ({@link #sections()}).
 *
 * @param compositions in order: on the same attribute a later one wins (intensity: the highest)
 * @param looks        looks used directly, applied before the compositions
 * @param sections     the light of the parts after the markers of the script, by marker name; a marker without one
 *                     is dark
 */
public record Scene(long id, long projectId, SceneSpec spec, List<SceneComposition> compositions,
		List<SceneKey> keys, List<SceneLook> looks, List<SceneSection> sections) {

	public Scene {
		Objects.requireNonNull(spec, "spec");
		compositions = List.copyOf(compositions);
		keys = List.copyOf(keys);
		looks = List.copyOf(looks);
		sections = List.copyOf(sections);
		SceneSection.validate(compositions, looks, "the scene");
		var slots = new HashSet<Integer>();
		var markers = new HashSet<String>();
		new Validation()
				.require(keys.stream().allMatch(k -> slots.add(k.slot())), "a key is used twice")
				.require(sections.stream().allMatch(s -> markers.add(s.marker())), "a marker has two parts")
				.throwIfInvalid();
	}

	/** A scene with compositions and keys only. */
	public Scene(long id, long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys) {
		this(id, projectId, spec, compositions, keys, List.of(), List.of());
	}

	/** A scene without compositions and keys yet. */
	public Scene(long id, long projectId, SceneSpec spec) {
		this(id, projectId, spec, List.of(), List.of());
	}

	/** The light of the part after a marker; empty (dark) if none is chosen. */
	public SceneSection section(String marker) {
		return sections.stream().filter(s -> s.marker().equals(marker)).findFirst()
				.orElse(new SceneSection(marker, List.of(), List.of()));
	}

	/** The scene with other compositions and looks at its start (before the first marker). */
	public Scene withStart(List<SceneComposition> newCompositions, List<SceneLook> newLooks) {
		return new Scene(id, projectId, spec, newCompositions, keys, newLooks, sections);
	}

	/** The scene with the light of a part replaced (an empty part is dropped). */
	public Scene withSection(SceneSection section) {
		var others = new ArrayList<>(sections.stream().filter(s -> !s.marker().equals(section.marker()))
				.toList());
		if (!section.isEmpty()) {
			others.add(section);
		}
		return new Scene(id, projectId, spec, compositions, keys, looks, others);
	}

	/** Whether the composition plays anywhere in the scene (start or a part after a marker). */
	public boolean contains(long compositionId) {
		return compositions.stream().anyMatch(c -> c.compositionId() == compositionId)
				|| sections.stream().anyMatch(s -> s.compositions().stream()
						.anyMatch(c -> c.compositionId() == compositionId));
	}
}
