package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteSceneRepository implements SceneRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteSceneRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Scene> findByProject(long projectId) {
		var rows = jdbc.sql("SELECT * FROM scene WHERE project_id = ?").param(projectId).query(this::row).list();
		var values = valuesOfProject(projectId);
		return rows.stream()
				.map(row -> row.toScene(values.getOrDefault(row.id(), Map.of())))
				.sorted(Comparator.comparing(scene -> scene.spec().number()))
				.toList();
	}

	@Override
	public Optional<Scene> findById(long id) {
		return jdbc.sql("SELECT * FROM scene WHERE id = ?").param(id).query(this::row).optional()
				.map(row -> row.toScene(valuesOfScene(id)));
	}

	@Override
	public Scene create(long projectId, SceneSpec spec, Map<Long, FixtureValues> values) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO scene (project_id, number, name, cue_note, auto_follow_ms, key_only)
					VALUES (?, ?, ?, ?, ?, ?)""")
					.params(projectId, spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
							autoFollowMs(spec), spec.keyOnly() ? 1 : 0)
					.update(keys);
			var sceneId = generatedId(keys);
			insertValues(sceneId, values);
			return sceneId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene updateSpec(long id, SceneSpec spec) {
		jdbc.sql("UPDATE scene SET number = ?, name = ?, cue_note = ?, auto_follow_ms = ?, key_only = ? WHERE id = ?")
				.params(spec.number().toString(), spec.name(), spec.cueNote().orElse(null), autoFollowMs(spec),
						spec.keyOnly() ? 1 : 0, id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public Scene replaceValues(long id, Map<Long, FixtureValues> values) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_value WHERE scene_id = ?").param(id).update();
			insertValues(id, values);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM scene WHERE id = ?").param(id).update();
	}

	private void insertValues(long sceneId, Map<Long, FixtureValues> values) {
		values.forEach((fixtureId, fixtureValues) -> fixtureValues.values().forEach((attribute, value) ->
				jdbc.sql("INSERT INTO scene_value (scene_id, fixture_id, attribute, value) VALUES (?, ?, ?, ?)")
						.params(sceneId, fixtureId, attribute.id(), value)
						.update()));
	}

	private Map<Long, FixtureValues> valuesOfScene(long sceneId) {
		return collect(jdbc.sql("SELECT * FROM scene_value WHERE scene_id = ?").param(sceneId)
				.query(this::valueRow).list()).getOrDefault(sceneId, Map.of());
	}

	private Map<Long, Map<Long, FixtureValues>> valuesOfProject(long projectId) {
		return collect(jdbc.sql("""
				SELECT v.* FROM scene_value v JOIN scene s ON s.id = v.scene_id WHERE s.project_id = ?""")
				.param(projectId).query(this::valueRow).list());
	}

	private static Map<Long, Map<Long, FixtureValues>> collect(List<ValueRow> rows) {
		var raw = new HashMap<Long, Map<Long, Map<Attribute, Double>>>();
		for (var row : rows) {
			raw.computeIfAbsent(row.sceneId(), id -> new HashMap<>())
					.computeIfAbsent(row.fixtureId(), id -> new HashMap<>())
					.put(row.attribute(), row.value());
		}
		var result = new HashMap<Long, Map<Long, FixtureValues>>();
		raw.forEach((sceneId, fixtures) -> {
			var perFixture = new HashMap<Long, FixtureValues>();
			fixtures.forEach((fixtureId, values) -> perFixture.put(fixtureId, new FixtureValues(values)));
			result.put(sceneId, perFixture);
		});
		return result;
	}

	private static Long autoFollowMs(SceneSpec spec) {
		return spec.autoFollow().map(Duration::toMillis).orElse(null);
	}

	private SceneRow row(ResultSet rs, int rowNum) throws SQLException {
		var autoFollow = rs.getLong("auto_follow_ms");
		var hasAutoFollow = !rs.wasNull();
		return new SceneRow(rs.getLong("id"), rs.getLong("project_id"), new SceneSpec(
				SceneNumber.parse(rs.getString("number")),
				rs.getString("name"),
				Optional.ofNullable(rs.getString("cue_note")),
				hasAutoFollow ? Optional.of(Duration.ofMillis(autoFollow)) : Optional.empty(),
				rs.getInt("key_only") == 1));
	}

	private ValueRow valueRow(ResultSet rs, int rowNum) throws SQLException {
		return new ValueRow(rs.getLong("scene_id"), rs.getLong("fixture_id"), Attribute.parse(rs.getString("attribute")),
				rs.getDouble("value"));
	}

	private record SceneRow(long id, long projectId, SceneSpec spec) {

		Scene toScene(Map<Long, FixtureValues> values) {
			return new Scene(id, projectId, spec, values);
		}
	}

	private record ValueRow(long sceneId, long fixtureId, Attribute attribute, double value) {
	}
}
