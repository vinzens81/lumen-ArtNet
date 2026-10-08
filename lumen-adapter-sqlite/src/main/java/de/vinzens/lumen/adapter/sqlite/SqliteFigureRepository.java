package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.move.port.out.FigureRepository;
import de.vinzens.lumen.domain.move.Figure;
import de.vinzens.lumen.domain.move.FigurePoint;
import de.vinzens.lumen.domain.move.FigureSpec;
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

/** Figures with their points. */
public class SqliteFigureRepository implements FigureRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteFigureRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Figure> findByProject(long projectId) {
		var points = points("figure_id IN (SELECT id FROM figure WHERE project_id = ?)", projectId);
		return jdbc.sql("SELECT * FROM figure WHERE project_id = ? ORDER BY name COLLATE NOCASE, id")
				.param(projectId).query((rs, n) -> figure(rs, points)).list();
	}

	@Override
	public Optional<Figure> findById(long id) {
		var points = points("figure_id = ?", id);
		return jdbc.sql("SELECT * FROM figure WHERE id = ?").param(id).query((rs, n) -> figure(rs, points))
				.optional();
	}

	@Override
	public Figure create(long projectId, FigureSpec spec) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO figure (project_id, name, closed) VALUES (?, ?, ?)")
					.params(projectId, spec.name(), spec.closed() ? 1 : 0)
					.update(keys);
			var figureId = generatedId(keys);
			insertPoints(figureId, spec.points());
			return figureId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Figure update(long id, FigureSpec spec) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("UPDATE figure SET name = ?, closed = ? WHERE id = ?")
					.params(spec.name(), spec.closed() ? 1 : 0, id).update();
			jdbc.sql("DELETE FROM figure_point WHERE figure_id = ?").param(id).update();
			insertPoints(id, spec.points());
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM figure WHERE id = ?").param(id).update();
	}

	private void insertPoints(long figureId, List<FigurePoint> points) {
		for (int i = 0; i < points.size(); i++) {
			jdbc.sql("INSERT INTO figure_point (figure_id, position, x, y) VALUES (?, ?, ?, ?)")
					.params(figureId, i, points.get(i).x(), points.get(i).y()).update();
		}
	}

	private Map<Long, List<FigurePoint>> points(String where, long param) {
		var result = new HashMap<Long, List<FigurePoint>>();
		jdbc.sql("SELECT * FROM figure_point WHERE " + where + " ORDER BY figure_id, position").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("figure_id"), id -> new ArrayList<>())
							.add(new FigurePoint(rs.getDouble("x"), rs.getDouble("y")));
				});
		return result;
	}

	private static Figure figure(ResultSet rs, Map<Long, List<FigurePoint>> points) throws SQLException {
		var id = rs.getLong("id");
		return new Figure(id, rs.getLong("project_id"), new FigureSpec(rs.getString("name"),
				rs.getInt("closed") == 1, points.getOrDefault(id, List.of())));
	}
}
