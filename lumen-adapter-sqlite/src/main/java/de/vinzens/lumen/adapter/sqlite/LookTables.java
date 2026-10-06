package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Looks stored as {@code <table>_value} (values per fixture and attribute) and {@code <table>_preset} (preset
 * references per fixture and attribute group), each row belonging to an owner: a look, a revision, or – in old
 * databases – a scene.
 */
final class LookTables {

	private final JdbcClient jdbc;

	LookTables(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(String table, String ownerColumn, long ownerId, Look look) {
		look.values().forEach((fixtureId, fixtureValues) -> fixtureValues.values().forEach((attribute, value) ->
				jdbc.sql("INSERT INTO " + table + "_value (" + ownerColumn + ", fixture_id, attribute, value) "
								+ "VALUES (?, ?, ?, ?)")
						.params(ownerId, fixtureId, attribute.id(), value)
						.update()));
		look.presets().refs().forEach((fixtureId, groups) -> groups.forEach((group, presetId) ->
				jdbc.sql("INSERT INTO " + table + "_preset (" + ownerColumn + ", fixture_id, attribute_group, "
								+ "preset_id) VALUES (?, ?, ?, ?)")
						.params(ownerId, fixtureId, group.id(), presetId)
						.update()));
	}

	void delete(String table, String ownerColumn, long ownerId) {
		jdbc.sql("DELETE FROM " + table + "_value WHERE " + ownerColumn + " = ?").param(ownerId).update();
		jdbc.sql("DELETE FROM " + table + "_preset WHERE " + ownerColumn + " = ?").param(ownerId).update();
	}

	/**
	 * Looks per owner. {@code where} selects owners, e.g. {@code "look_id IN (SELECT id FROM look WHERE
	 * project_id = ?)"}, with {@code param} as its parameter.
	 */
	Map<Long, Look> load(String table, String ownerColumn, String where, Object param) {
		var values = new HashMap<Long, Map<Long, Map<Attribute, Double>>>();
		jdbc.sql("SELECT * FROM " + table + "_value WHERE " + where).param(param).query(rs -> {
			values.computeIfAbsent(rs.getLong(ownerColumn), id -> new HashMap<>())
					.computeIfAbsent(rs.getLong("fixture_id"), id -> new HashMap<>())
					.put(Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
		});
		var presets = new HashMap<Long, Map<Long, Map<AttributeGroup, Long>>>();
		jdbc.sql("SELECT * FROM " + table + "_preset WHERE " + where).param(param).query(rs -> {
			presets.computeIfAbsent(rs.getLong(ownerColumn), id -> new HashMap<>())
					.computeIfAbsent(rs.getLong("fixture_id"), id -> new HashMap<>())
					.put(AttributeGroup.parse(rs.getString("attribute_group")), rs.getLong("preset_id"));
		});
		var owners = new HashSet<>(values.keySet());
		owners.addAll(presets.keySet());
		var result = new HashMap<Long, Look>();
		for (var owner : owners) {
			var perFixture = new HashMap<Long, FixtureValues>();
			values.getOrDefault(owner, Map.of()).forEach((fixtureId, attributes) ->
					perFixture.put(fixtureId, new FixtureValues(attributes)));
			result.put(owner, new Look(perFixture, new PresetRefs(presets.getOrDefault(owner, Map.of()))));
		}
		return result;
	}
}
