package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.move.port.out.MoveRepository;
import de.vinzens.lumen.domain.move.Move;
import de.vinzens.lumen.domain.move.MoveFixture;
import de.vinzens.lumen.domain.move.MoveRepeat;
import de.vinzens.lumen.domain.move.MoveSpec;
import de.vinzens.lumen.domain.move.OnceStart;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

/** Moves with their fixtures. */
public class SqliteMoveRepository implements MoveRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteMoveRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Move> findByProject(long projectId) {
		var fixtures = fixtures("move_id IN (SELECT id FROM move WHERE project_id = ?)", projectId);
		return jdbc.sql("SELECT * FROM move WHERE project_id = ? ORDER BY name COLLATE NOCASE, id")
				.param(projectId).query((rs, n) -> move(rs, fixtures)).list();
	}

	@Override
	public Optional<Move> findById(long id) {
		var fixtures = fixtures("move_id = ?", id);
		return jdbc.sql("SELECT * FROM move WHERE id = ?").param(id).query((rs, n) -> move(rs, fixtures)).optional();
	}

	@Override
	public Move create(long projectId, MoveSpec spec) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO move (project_id, name, figure_id, size_x, size_y, shift_x, shift_y, rotation_deg,
					                  duration_ms, repeat_mode, step_offset, once_start)
					VALUES (?, ?, ?, 1, 1, 0, 0, 0, 1, 'loop', 0, 'sequential')""")
					.params(projectId, spec.name(), spec.figureId())
					.update(keys);
			var moveId = generatedId(keys);
			write(moveId, spec);
			return moveId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Move update(long id, MoveSpec spec) {
		transactions.executeWithoutResult(status -> write(id, spec));
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM move WHERE id = ?").param(id).update();
	}

	private void write(long id, MoveSpec spec) {
		jdbc.sql("""
				UPDATE move SET name = ?, figure_id = ?, size_x = ?, size_y = ?, shift_x = ?, shift_y = ?,
				                rotation_deg = ?, duration_ms = ?, repeat_mode = ?, step_offset = ?, once_start = ?
				WHERE id = ?""")
				.params(spec.name(), spec.figureId(), spec.sizeX(), spec.sizeY(), spec.shiftX(), spec.shiftY(),
						spec.rotationDegrees(), Math.round(spec.durationSeconds() * 1000), spec.repeat().id(),
						spec.stepOffset(), spec.onceStart().id(), id)
				.update();
		jdbc.sql("DELETE FROM move_fixture WHERE move_id = ?").param(id).update();
		for (int i = 0; i < spec.fixtures().size(); i++) {
			var fixture = spec.fixtures().get(i);
			jdbc.sql("""
					INSERT INTO move_fixture (move_id, position, fixture_id, shift_x, shift_y, mirror_x, mirror_y,
					                          reverse)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(id, i, fixture.fixtureId(), fixture.shiftX(), fixture.shiftY(), fixture.mirrorX() ? 1 : 0,
							fixture.mirrorY() ? 1 : 0, fixture.reverse() ? 1 : 0)
					.update();
		}
	}

	private Map<Long, List<MoveFixture>> fixtures(String where, long param) {
		var result = new HashMap<Long, List<MoveFixture>>();
		jdbc.sql("SELECT * FROM move_fixture WHERE " + where + " ORDER BY move_id, position").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("move_id"), id -> new ArrayList<>()).add(new MoveFixture(
							rs.getLong("fixture_id"), rs.getDouble("shift_x"), rs.getDouble("shift_y"),
							rs.getInt("mirror_x") == 1, rs.getInt("mirror_y") == 1, rs.getInt("reverse") == 1));
				});
		return result;
	}

	private static Move move(ResultSet rs, Map<Long, List<MoveFixture>> fixtures) throws SQLException {
		var id = rs.getLong("id");
		var spec = new MoveSpec(rs.getString("name"), rs.getLong("figure_id"), fixtures.getOrDefault(id, List.of()),
				rs.getDouble("size_x"), rs.getDouble("size_y"), rs.getDouble("shift_x"), rs.getDouble("shift_y"),
				rs.getDouble("rotation_deg"), rs.getLong("duration_ms") / 1000.0,
				MoveRepeat.fromId(rs.getString("repeat_mode")), rs.getInt("step_offset"),
				OnceStart.fromId(rs.getString("once_start")));
		return new Move(id, rs.getLong("project_id"), spec);
	}
}
