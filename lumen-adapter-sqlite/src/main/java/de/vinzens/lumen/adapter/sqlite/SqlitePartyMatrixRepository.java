package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.party.port.out.PartyMatrixRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.party.PartyCell;
import de.vinzens.lumen.domain.party.PartyFader;
import de.vinzens.lumen.domain.party.PartyMatrix;
import de.vinzens.lumen.domain.party.PartyPage;
import de.vinzens.lumen.domain.party.PartyPlace;
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
		var faderFixtures = new HashMap<PartyPlace, List<Long>>();
		jdbc.sql("""
				SELECT page_index, row_index, column_index, fixture_id FROM party_cell_fixture WHERE project_id = ?
				ORDER BY page_index, row_index, column_index, position""")
				.param(projectId)
				.query(rs -> {
					faderFixtures.computeIfAbsent(place(rs), place -> new ArrayList<>()).add(rs.getLong("fixture_id"));
				});
		var cells = new HashMap<Integer, List<PartyCell>>();
		jdbc.sql("""
				SELECT * FROM party_cell WHERE project_id = ? ORDER BY page_index, row_index, column_index""")
				.param(projectId)
				.query(rs -> {
					cells.computeIfAbsent(rs.getInt("page_index"), page -> new ArrayList<>()).add(cell(rs, faderFixtures));
				});
		var pages = jdbc.sql("SELECT * FROM party_page WHERE project_id = ? ORDER BY page_index").param(projectId)
				.query((rs, n) -> new PartyPage(rs.getString("name"), rs.getInt("rows"), rs.getInt("columns"),
						separators(rs.getString("separators")), cells.getOrDefault(rs.getInt("page_index"), List.of())))
				.list();
		return pages.isEmpty() ? PartyMatrix.EMPTY : new PartyMatrix(pages);
	}

	@Override
	public void save(long projectId, PartyMatrix matrix) {
		transactions.executeWithoutResult(status -> {
			// cells and their fixtures go with their pages
			jdbc.sql("DELETE FROM party_page WHERE project_id = ?").param(projectId).update();
			for (int index = 0; index < matrix.pages().size(); index++) {
				var page = matrix.pages().get(index);
				jdbc.sql("""
						INSERT INTO party_page (project_id, page_index, name, rows, columns, separators)
						VALUES (?, ?, ?, ?, ?, ?)""")
						.params(projectId, index, page.name(), page.rows(), page.columns(), page.separators().stream()
								.sorted().map(String::valueOf).collect(Collectors.joining(",")))
						.update();
				for (var cell : page.cells()) {
					insert(projectId, index, cell);
				}
			}
		});
	}

	private void insert(long projectId, int page, PartyCell cell) {
		jdbc.sql("""
				INSERT INTO party_cell (project_id, page_index, row_index, column_index, label, scene_id, composition_id,
				                        move_id, color, border_color, key_mode, group_name, span, row_span, fader_kind)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(projectId, page, cell.row(), cell.column(), cell.label().orElse(null),
						cell.sceneId().orElse(null), cell.compositionId().orElse(null), cell.moveId().orElse(null),
						cell.color(), cell.borderColor(), cell.mode().id(), cell.group().orElse(null), cell.span(),
						cell.rowSpan(), cell.fader().map(fader -> fader.kind().id()).orElse(null))
				.update();
		var fixtureIds = cell.fader().map(PartyFader::fixtureIds).orElse(List.of());
		for (int position = 0; position < fixtureIds.size(); position++) {
			jdbc.sql("""
					INSERT INTO party_cell_fixture (project_id, page_index, row_index, column_index, position,
					                                fixture_id)
					VALUES (?, ?, ?, ?, ?, ?)""")
					.params(projectId, page, cell.row(), cell.column(), position, fixtureIds.get(position))
					.update();
		}
	}

	private static PartyPlace place(ResultSet rs) throws SQLException {
		return new PartyPlace(rs.getInt("page_index"), rs.getInt("row_index"), rs.getInt("column_index"));
	}

	/** A gradient or pad whose fixtures are all deleted leaves a text field. */
	private static PartyCell cell(ResultSet rs, Map<PartyPlace, List<Long>> faderFixtures) throws SQLException {
		var fixtureIds = faderFixtures.getOrDefault(place(rs), List.of());
		var fader = Optional.ofNullable(rs.getString("fader_kind"))
				.filter(kind -> !fixtureIds.isEmpty())
				.map(kind -> new PartyFader(PartyFader.Kind.parse(kind), fixtureIds));
		return new PartyCell(rs.getInt("row_index"), rs.getInt("column_index"),
				Optional.ofNullable(rs.getString("label")), optionalLong(rs, "scene_id"),
				optionalLong(rs, "composition_id"), optionalLong(rs, "move_id"), rs.getString("color"),
				rs.getString("border_color"), KeyMode.parse(rs.getString("key_mode")),
				Optional.ofNullable(rs.getString("group_name")), rs.getInt("span"), rs.getInt("row_span"), fader);
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
