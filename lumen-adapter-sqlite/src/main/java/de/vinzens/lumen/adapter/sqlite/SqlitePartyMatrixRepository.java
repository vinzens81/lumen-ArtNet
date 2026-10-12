package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SqlitePartyMatrixRepository implements PartyMatrixRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqlitePartyMatrixRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public PartyMatrix find(long projectId) {
		var faderFixtures = new HashMap<PartyCell.Position, List<Long>>();
		jdbc.sql("""
				SELECT row_index, column_index, fixture_id FROM party_cell_fixture WHERE project_id = ?
				ORDER BY row_index, column_index, position""")
				.param(projectId)
				.query(rs -> {
					faderFixtures.computeIfAbsent(new PartyCell.Position(rs.getInt("row_index"),
							rs.getInt("column_index")), position -> new ArrayList<>()).add(rs.getLong("fixture_id"));
				});
		var cells = jdbc.sql("SELECT * FROM party_cell WHERE project_id = ? ORDER BY row_index, column_index")
				.param(projectId).query((rs, n) -> cell(rs, faderFixtures)).list();
		return jdbc.sql("SELECT * FROM party_matrix WHERE project_id = ?").param(projectId)
				.query((rs, n) -> new PartyMatrix(rs.getInt("rows"), rs.getInt("columns"),
						separators(rs.getString("separators")), cells))
				.optional()
				.orElse(PartyMatrix.EMPTY);
	}

	@Override
	public void save(long projectId, PartyMatrix matrix) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("""
					INSERT INTO party_matrix (project_id, rows, columns, separators) VALUES (?, ?, ?, ?)
					ON CONFLICT (project_id) DO UPDATE SET rows = excluded.rows, columns = excluded.columns,
					    separators = excluded.separators""")
					.params(projectId, matrix.rows(), matrix.columns(), matrix.separators().stream().sorted()
							.map(String::valueOf).collect(Collectors.joining(",")))
					.update();
			jdbc.sql("DELETE FROM party_cell WHERE project_id = ?").param(projectId).update();
			for (var cell : matrix.cells()) {
				jdbc.sql("""
						INSERT INTO party_cell (project_id, row_index, column_index, label, scene_id, composition_id,
						                        move_id, color, border_color, key_mode, group_name, span, fader_kind)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
						.params(projectId, cell.row(), cell.column(), cell.label().orElse(null),
								cell.sceneId().orElse(null), cell.compositionId().orElse(null),
								cell.moveId().orElse(null), cell.color(), cell.borderColor(), cell.mode().id(),
								cell.group().orElse(null), cell.span(),
								cell.fader().map(fader -> fader.kind().id()).orElse(null))
						.update();
				var fixtureIds = cell.fader().map(PartyFader::fixtureIds).orElse(List.of());
				for (int position = 0; position < fixtureIds.size(); position++) {
					jdbc.sql("""
							INSERT INTO party_cell_fixture (project_id, row_index, column_index, position, fixture_id)
							VALUES (?, ?, ?, ?, ?)""")
							.params(projectId, cell.row(), cell.column(), position, fixtureIds.get(position))
							.update();
				}
			}
		});
	}

	/** A gradient whose fixtures are all deleted leaves a text field. */
	private static PartyCell cell(ResultSet rs, Map<PartyCell.Position, List<Long>> faderFixtures)
			throws SQLException {
		var row = rs.getInt("row_index");
		var column = rs.getInt("column_index");
		var fixtureIds = faderFixtures.getOrDefault(new PartyCell.Position(row, column), List.of());
		var fader = Optional.ofNullable(rs.getString("fader_kind"))
				.filter(kind -> !fixtureIds.isEmpty())
				.map(kind -> new PartyFader(PartyFader.Kind.parse(kind), fixtureIds));
		return new PartyCell(row, column, Optional.ofNullable(rs.getString("label")), optionalLong(rs, "scene_id"),
				optionalLong(rs, "composition_id"), optionalLong(rs, "move_id"), rs.getString("color"),
				rs.getString("border_color"), KeyMode.parse(rs.getString("key_mode")),
				Optional.ofNullable(rs.getString("group_name")), rs.getInt("span"), fader);
	}

	private static Optional<Long> optionalLong(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(value);
	}

	private static Set<Integer> separators(String text) {
		if (text == null || text.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(text.split(",")).map(String::strip).map(Integer::valueOf).collect(Collectors.toSet());
	}
}
