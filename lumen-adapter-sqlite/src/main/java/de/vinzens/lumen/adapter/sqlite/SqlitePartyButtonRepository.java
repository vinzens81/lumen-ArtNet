package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.party.port.out.PartyButtonRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.party.PartyButton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqlitePartyButtonRepository implements PartyButtonRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqlitePartyButtonRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<PartyButton> findByProject(long projectId) {
		var rows = jdbc.sql("SELECT * FROM party_button WHERE project_id = ? ORDER BY position, id").param(projectId)
				.query(this::row).list();
		var values = values("""
				SELECT v.* FROM party_button_value v JOIN party_button b ON b.id = v.button_id
				WHERE b.project_id = ?""", projectId);
		return rows.stream().map(row -> row.toButton(values.getOrDefault(row.id(), Map.of()))).toList();
	}

	@Override
	public Optional<PartyButton> findById(long id) {
		return jdbc.sql("SELECT * FROM party_button WHERE id = ?").param(id).query(this::row).optional()
				.map(row -> row.toButton(values("SELECT * FROM party_button_value WHERE button_id = ?", id)
						.getOrDefault(id, Map.of())));
	}

	@Override
	public PartyButton create(long projectId, String name, KeyMode mode, Map<Long, FixtureValues> values) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO party_button (project_id, position, name, key_mode)
					VALUES (?, (SELECT COALESCE(MAX(position), 0) + 1 FROM party_button WHERE project_id = ?), ?, ?)""")
					.params(projectId, projectId, name.strip(), mode.id())
					.update(keys);
			var buttonId = generatedId(keys);
			insertValues(buttonId, values);
			return buttonId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public PartyButton update(long id, String name, KeyMode mode) {
		jdbc.sql("UPDATE party_button SET name = ?, key_mode = ? WHERE id = ?").params(name.strip(), mode.id(), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public PartyButton replaceValues(long id, Map<Long, FixtureValues> values) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM party_button_value WHERE button_id = ?").param(id).update();
			insertValues(id, values);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void reorder(List<Long> ids) {
		transactions.executeWithoutResult(status -> {
			for (int i = 0; i < ids.size(); i++) {
				jdbc.sql("UPDATE party_button SET position = ? WHERE id = ?").params(i + 1, ids.get(i)).update();
			}
		});
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM party_button WHERE id = ?").param(id).update();
	}

	private void insertValues(long buttonId, Map<Long, FixtureValues> values) {
		values.forEach((fixtureId, fixtureValues) -> fixtureValues.values().forEach((attribute, value) ->
				jdbc.sql("INSERT INTO party_button_value (button_id, fixture_id, attribute, value) VALUES (?, ?, ?, ?)")
						.params(buttonId, fixtureId, attribute.id(), value)
						.update()));
	}

	/** Values per button id and fixture id. */
	private Map<Long, Map<Long, FixtureValues>> values(String sql, long param) {
		var raw = new HashMap<Long, Map<Long, Map<Attribute, Double>>>();
		jdbc.sql(sql).param(param).query(rs -> {
			raw.computeIfAbsent(rs.getLong("button_id"), id -> new HashMap<>())
					.computeIfAbsent(rs.getLong("fixture_id"), id -> new HashMap<>())
					.put(Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
		});
		var result = new HashMap<Long, Map<Long, FixtureValues>>();
		raw.forEach((buttonId, fixtures) -> {
			var perFixture = new HashMap<Long, FixtureValues>();
			fixtures.forEach((fixtureId, values) -> perFixture.put(fixtureId, new FixtureValues(values)));
			result.put(buttonId, perFixture);
		});
		return result;
	}

	private ButtonRow row(ResultSet rs, int rowNum) throws SQLException {
		return new ButtonRow(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("position"), rs.getString("name"),
				KeyMode.valueOf(rs.getString("key_mode").toUpperCase(Locale.ROOT)));
	}

	private record ButtonRow(long id, long projectId, int position, String name, KeyMode mode) {

		PartyButton toButton(Map<Long, FixtureValues> values) {
			return new PartyButton(id, projectId, position, name, mode, values);
		}
	}
}
