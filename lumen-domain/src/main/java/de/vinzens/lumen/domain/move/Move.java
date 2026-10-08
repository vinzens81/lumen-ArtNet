package de.vinzens.lumen.domain.move;

import java.util.Objects;

/**
 * A move of the project, e.g. "Eight above the stage": where pan/tilt fixtures point, following a figure or standing
 * at its single point. Scenes, light markers, party fields and keys play moves next to looks and compositions; pan
 * and tilt come only from moves.
 */
public record Move(long id, long projectId, MoveSpec spec) {

	public Move {
		Objects.requireNonNull(spec, "spec");
	}
}
