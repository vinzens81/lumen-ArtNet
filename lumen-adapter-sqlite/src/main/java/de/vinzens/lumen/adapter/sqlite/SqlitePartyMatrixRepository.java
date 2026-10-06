package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyMatrix;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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
		var cells = jdbc.sql("SELECT * FROM party_cell WHERE project_id = ? ORDER BY row_index, column_index")
				.param(projectId).query((rs, n) -> cell(rs)).list();
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
						                        color, border_color, key_mode, group_name)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
						.params(projectId, cell.row(), cell.column(), cell.label().orElse(null),
								cell.sceneId().orElse(null), cell.compositionId().orElse(null), cell.color(),
								cell.borderColor(), cell.mode().id(), cell.group().orElse(null))
						.update();
			}
		});
	}

	private static PartyCell cell(ResultSet rs) throws SQLException {
		return new PartyCell(rs.getInt("row_index"), rs.getInt("column_index"),
				Optional.ofNullable(rs.getString("label")), optionalLong(rs, "scene_id"),
				optionalLong(rs, "composition_id"), rs.getString("color"), rs.getString("border_color"),
				KeyMode.parse(rs.getString("key_mode")), Optional.ofNullable(rs.getString("group_name")));
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
