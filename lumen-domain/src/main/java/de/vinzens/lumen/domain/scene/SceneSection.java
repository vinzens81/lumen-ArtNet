package de.vinzens.lumen.domain.scene;

import de.vinzens.lumen.domain.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The light of a part of a scene: from a marker in the script ({@code //## Marker (info)##}) to the next. Every part
 * stands on its own – what should stay on over the whole scene is chosen again for each part.
 *
 * @param marker the name of the marker in the script
 * @param looks  applied first, the compositions over them
 * @param moves  where the pan/tilt fixtures point; on the same fixture a later move wins
 */
public record SceneSection(String marker, List<SceneComposition> compositions, List<SceneLook> looks,
		List<SceneMove> moves) {

	public SceneSection {
		Objects.requireNonNull(marker, "marker");
		compositions = List.copyOf(compositions);
		looks = List.copyOf(looks);
		moves = List.copyOf(moves);
		validate(compositions, looks, moves, "the part " + marker);
	}

	/** A part without moves. */
	public SceneSection(String marker, List<SceneComposition> compositions, List<SceneLook> looks) {
		this(marker, compositions, looks, List.of());
	}

	public boolean isEmpty() {
		return compositions.isEmpty() && looks.isEmpty() && moves.isEmpty();
	}

	static void validate(List<SceneComposition> compositions, List<SceneLook> looks, List<SceneMove> moves,
			String where) {
		var compositionIds = new HashSet<Long>();
		var lookIds = new HashSet<Long>();
		var moveIds = new HashSet<Long>();
		new Validation()
				.require(compositions.stream().allMatch(c -> compositionIds.add(c.compositionId())),
						"a composition is in " + where + " twice")
				.require(looks.stream().allMatch(l -> lookIds.add(l.lookId())), "a look is in " + where + " twice")
				.require(moves.stream().allMatch(m -> moveIds.add(m.moveId())), "a move is in " + where + " twice")
				.throwIfInvalid();
	}
}
