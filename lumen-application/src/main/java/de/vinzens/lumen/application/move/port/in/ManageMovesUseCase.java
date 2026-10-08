package de.vinzens.lumen.application.move.port.in;

import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveSpec;

import java.util.List;
import java.util.Optional;

/**
 * Moves of a project: a figure laid onto pan/tilt fixtures with size, shift, rotation, tempo and offset. Scenes,
 * light markers, party fields and keys play them; pan and tilt come only from moves.
 */
public interface ManageMovesUseCase {

	/** By name. */
	List<Move> moves(long projectId);

	Move move(long projectId, long moveId);

	/** The figure and the fixtures must belong to the project; every fixture needs pan and tilt. */
	Move create(long projectId, MoveSpec spec);

	Move update(long projectId, long moveId, MoveSpec spec);

	/** A copy, played nowhere. */
	Move duplicate(long projectId, long moveId);

	/** Deletes a move that nothing plays; otherwise a conflict names the scenes, party fields and keys. */
	void delete(long projectId, long moveId);

	/**
	 * "Record position": the positions of the given fixtures in the programmer become a move with one point. The point
	 * is the middle of their positions, each fixture gets its distance from it as its own shift, so all point where
	 * they point now. Only fixtures with pan and tilt in the programmer count. Active project only.
	 *
	 * @param moveId the move to update (its figure becomes the point), empty for a new move and figure named
	 *               {@code name}
	 */
	Move recordPosition(long projectId, Optional<Long> moveId, String name, List<Long> fixtureIds);
}
