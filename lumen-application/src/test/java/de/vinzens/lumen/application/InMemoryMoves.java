package de.vinzens.lumen.application;

import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Moves in memory, for tests. */
public class InMemoryMoves implements MoveRepository {

	private final Map<Long, Move> moves = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public List<Move> findByProject(long projectId) {
		return moves.values().stream().filter(m -> m.projectId() == projectId).toList();
	}

	@Override
	public Optional<Move> findById(long id) {
		return Optional.ofNullable(moves.get(id));
	}

	@Override
	public Move create(long projectId, MoveSpec spec) {
		var move = new Move(nextId++, projectId, spec);
		moves.put(move.id(), move);
		return move;
	}

	@Override
	public Move update(long id, MoveSpec spec) {
		var move = new Move(id, moves.get(id).projectId(), spec);
		moves.put(id, move);
		return move;
	}

	@Override
	public void delete(long id) {
		moves.remove(id);
	}
}
