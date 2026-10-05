package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.preset.port.out.PresetRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.preset.Preset;
import de.vinzens.lumen.domain.preset.PresetSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqlitePresetRepository implements PresetRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqlitePresetRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Preset> findByProject(long projectId) {
		var values = values("""
				SELECT v.* FROM preset_value v JOIN preset p ON p.id = v.preset_id WHERE p.project_id = ?""",
				projectId);
		return jdbc.sql("SELECT * FROM preset WHERE project_id = ? ORDER BY id").param(projectId)
				.query((rs, rowNum) -> preset(rs, values)).list();
	}

	@Override
	public Optional<Preset> findById(long id) {
		var values = values("SELECT * FROM preset_value WHERE preset_id = ?", id);
		return jdbc.sql("SELECT * FROM preset WHERE id = ?").param(id).query((rs, rowNum) -> preset(rs, values))
				.optional();
	}

	@Override
	public Preset create(long projectId, PresetSpec spec, Map<Long, FixtureValues> values) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO preset (project_id, name, attribute_group) VALUES (?, ?, ?)")
					.params(projectId, spec.name(), spec.group().id())
					.update(keys);
			var presetId = generatedId(keys);
			insertValues(presetId, values);
			return presetId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Preset updateSpec(long id, PresetSpec spec) {
		jdbc.sql("UPDATE preset SET name = ?, attribute_group = ? WHERE id = ?")
				.params(spec.name(), spec.group().id(), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public Preset replaceValues(long id, Map<Long, FixtureValues> values) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM preset_value WHERE preset_id = ?").param(id).update();
			insertValues(id, values);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM preset WHERE id = ?").param(id).update();
	}

	private void insertValues(long presetId, Map<Long, FixtureValues> values) {
		values.forEach((fixtureId, fixtureValues) -> fixtureValues.values().forEach((attribute, value) ->
				jdbc.sql("INSERT INTO preset_value (preset_id, fixture_id, attribute, value) VALUES (?, ?, ?, ?)")
						.params(presetId, fixtureId, attribute.id(), value)
						.update()));
	}

	/** Values per preset id and fixture id. */
	private Map<Long, Map<Long, FixtureValues>> values(String sql, long param) {
		var raw = new HashMap<Long, Map<Long, Map<Attribute, Double>>>();
		jdbc.sql(sql).param(param).query(rs -> {
			raw.computeIfAbsent(rs.getLong("preset_id"), id -> new HashMap<>())
					.computeIfAbsent(rs.getLong("fixture_id"), id -> new HashMap<>())
					.put(Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
		});
		var result = new HashMap<Long, Map<Long, FixtureValues>>();
		raw.forEach((presetId, fixtures) -> {
			var perFixture = new HashMap<Long, FixtureValues>();
			fixtures.forEach((fixtureId, values) -> perFixture.put(fixtureId, new FixtureValues(values)));
			result.put(presetId, perFixture);
		});
		return result;
	}

	private static Preset preset(ResultSet rs, Map<Long, Map<Long, FixtureValues>> values) throws SQLException {
		var id = rs.getLong("id");
		return new Preset(id, rs.getLong("project_id"),
				new PresetSpec(rs.getString("name"), AttributeGroup.parse(rs.getString("attribute_group"))),
				values.getOrDefault(id, Map.of()));
	}
}
