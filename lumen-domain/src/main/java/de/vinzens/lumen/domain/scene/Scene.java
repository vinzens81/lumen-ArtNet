package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The third level of the show: a set of compositions (and looks) that run side by side from GO, and the extra keys
 * usable while the scene is on stage. Fixtures in none of them are dark in it. Light markers in the script split the
 * scene into parts with light of their own ({@link #sections()}).
 *
 * @param compositions in order: on the same attribute a later one wins (intensity: the highest)
 * @param looks        looks used directly, applied before the compositions
 * @param moves        where the pan/tilt fixtures point, applied after the compositions
 * @param sections     the light of the parts after the markers of the script, by marker name; a marker without one
 *                     is dark
 */
public record Scene(long id, long projectId, SceneSpec spec, List<SceneComposition> compositions,
		List<SceneKey> keys, List<SceneLook> looks, List<SceneMove> moves, List<SceneSection> sections) {

	public Scene {
		Objects.requireNonNull(spec, "spec");
		compositions = List.copyOf(compositions);
		keys = List.copyOf(keys);
		looks = List.copyOf(looks);
		moves = List.copyOf(moves);
		sections = List.copyOf(sections);
		SceneSection.validate(compositions, looks, moves, "the scene");
		var slots = new HashSet<Integer>();
		var markers = new HashSet<String>();
		new Validation()
				.require(keys.stream().allMatch(k -> slots.add(k.slot())), "a key is used twice")
				.require(sections.stream().allMatch(s -> markers.add(s.marker())), "a marker has two parts")
				.throwIfInvalid();
	}

	/** A scene without moves at its start. */
	public Scene(long id, long projectId, SceneSpec spec, List<SceneComposition> compositions,
			List<SceneKey> keys, List<SceneLook> looks, List<SceneSection> sections) {
		this(id, projectId, spec, compositions, keys, looks, List.of(), sections);
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

	/** The scene with other compositions and looks at its start (before the first marker); moves stay. */
	public Scene withStart(List<SceneComposition> newCompositions, List<SceneLook> newLooks) {
		return new Scene(id, projectId, spec, newCompositions, keys, newLooks, moves, sections);
	}

	/** The scene with other compositions, looks and moves at its start (before the first marker). */
	public Scene withStart(List<SceneComposition> newCompositions, List<SceneLook> newLooks,
			List<SceneMove> newMoves) {
		return new Scene(id, projectId, spec, newCompositions, keys, newLooks, newMoves, sections);
	}

	/** The scene with the light of a part replaced (an empty part is dropped). */
	public Scene withSection(SceneSection section) {
		var others = new ArrayList<>(sections.stream().filter(s -> !s.marker().equals(section.marker()))
				.toList());
		if (!section.isEmpty()) {
			others.add(section);
		}
		return new Scene(id, projectId, spec, compositions, keys, looks, moves, others);
	}

	/** Whether the move plays anywhere in the scene: at its start, in a part after a marker or on a key. */
	public boolean containsMove(long moveId) {
		return moves.stream().anyMatch(m -> m.moveId() == moveId)
				|| sections.stream().anyMatch(s -> s.moves().stream().anyMatch(m -> m.moveId() == moveId))
				|| keys.stream().anyMatch(k -> k.moveId().equals(Optional.of(moveId)));
	}

	/** Whether the composition plays anywhere in the scene (start or a part after a marker). */
	public boolean contains(long compositionId) {
		return compositions.stream().anyMatch(c -> c.compositionId() == compositionId)
				|| sections.stream().anyMatch(s -> s.compositions().stream()
						.anyMatch(c -> c.compositionId() == compositionId));
	}
}
