package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneRevision;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
					INSERT INTO scene (project_id, number, name, cue_note, auto_follow_ms, key_only, fade_in_ms,
					                   fade_out_ms, delay_ms, fade_curve, script)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(projectId, spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
							autoFollowMs(spec), spec.keyOnly() ? 1 : 0, millis(spec.timing().fadeIn()),
							millis(spec.timing().fadeOut()), spec.timing().delay().toMillis(), curve(spec),
							spec.script())
					.update(keys);
			var sceneId = generatedId(keys);
			insertValues(sceneId, values);
			return sceneId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene updateSpec(long id, SceneSpec spec) {
		jdbc.sql("""
				UPDATE scene SET number = ?, name = ?, cue_note = ?, auto_follow_ms = ?, key_only = ?, fade_in_ms = ?,
				                 fade_out_ms = ?, delay_ms = ?, fade_curve = ?, script = ?
				WHERE id = ?""")
				.params(spec.number().toString(), spec.name(), spec.cueNote().orElse(null), autoFollowMs(spec),
						spec.keyOnly() ? 1 : 0, millis(spec.timing().fadeIn()), millis(spec.timing().fadeOut()),
						spec.timing().delay().toMillis(), curve(spec), spec.script(), id)
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

	@Override
	public SceneRevision saveRevision(long sceneId, Instant createdAt, String reason, Map<Long, FixtureValues> values,
			int keep) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO scene_revision (scene_id, created_at, reason) VALUES (?, ?, ?)")
					.params(sceneId, createdAt.toString(), reason)
					.update(keys);
			var revisionId = generatedId(keys);
			values.forEach((fixtureId, fixtureValues) -> fixtureValues.values().forEach((attribute, value) ->
					jdbc.sql("""
							INSERT INTO scene_revision_value (revision_id, fixture_id, attribute, value)
							VALUES (?, ?, ?, ?)""")
							.params(revisionId, fixtureId, attribute.id(), value)
							.update()));
			jdbc.sql("""
					DELETE FROM scene_revision WHERE scene_id = ? AND id NOT IN (
					    SELECT id FROM scene_revision WHERE scene_id = ? ORDER BY id DESC LIMIT ?)""")
					.params(sceneId, sceneId, keep)
					.update();
			return revisionId;
		});
		return findRevision(id).orElseThrow();
	}

	@Override
	public List<SceneRevision> findRevisions(long sceneId) {
		var values = collect(jdbc.sql("""
				SELECT v.* FROM scene_revision_value v JOIN scene_revision r ON r.id = v.revision_id
				WHERE r.scene_id = ?""").param(sceneId).query(this::revisionValueRow).list());
		return jdbc.sql("SELECT * FROM scene_revision WHERE scene_id = ? ORDER BY id DESC").param(sceneId)
				.query((rs, rowNum) -> revision(rs, values)).list();
	}

	@Override
	public Optional<SceneRevision> findRevision(long id) {
		var values = collect(jdbc.sql("SELECT * FROM scene_revision_value WHERE revision_id = ?")
				.param(id).query(this::revisionValueRow).list());
		return jdbc.sql("SELECT * FROM scene_revision WHERE id = ?").param(id)
				.query((rs, rowNum) -> revision(rs, values)).optional();
	}

	private static SceneRevision revision(ResultSet rs, Map<Long, Map<Long, FixtureValues>> values)
			throws SQLException {
		var id = rs.getLong("id");
		return new SceneRevision(id, rs.getLong("scene_id"), Instant.parse(rs.getString("created_at")),
				rs.getString("reason"), values.getOrDefault(id, Map.of()));
	}

	private ValueRow revisionValueRow(ResultSet rs, int rowNum) throws SQLException {
		return new ValueRow(rs.getLong("revision_id"), rs.getLong("fixture_id"),
				Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
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

	/** Values per owner (scene or revision) and fixture. */
	private static Map<Long, Map<Long, FixtureValues>> collect(List<ValueRow> rows) {
		var raw = new HashMap<Long, Map<Long, Map<Attribute, Double>>>();
		for (var row : rows) {
			raw.computeIfAbsent(row.ownerId(), id -> new HashMap<>())
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

	private static String curve(SceneSpec spec) {
		return spec.timing().curve().map(FadeCurve::id).orElse(null);
	}

	private static Long autoFollowMs(SceneSpec spec) {
		return millis(spec.autoFollow());
	}

	private static Long millis(Optional<Duration> duration) {
		return duration.map(Duration::toMillis).orElse(null);
	}

	private SceneRow row(ResultSet rs, int rowNum) throws SQLException {
		var timing = new SceneTiming(optionalMillis(rs, "fade_in_ms"), optionalMillis(rs, "fade_out_ms"),
				Duration.ofMillis(rs.getLong("delay_ms")),
				Optional.ofNullable(rs.getString("fade_curve")).map(FadeCurve::parse));
		return new SceneRow(rs.getLong("id"), rs.getLong("project_id"), new SceneSpec(
				SceneNumber.parse(rs.getString("number")),
				rs.getString("name"),
				Optional.ofNullable(rs.getString("cue_note")),
				optionalMillis(rs, "auto_follow_ms"),
				rs.getInt("key_only") == 1,
				timing,
				rs.getString("script")));
	}

	private static Optional<Duration> optionalMillis(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(Duration.ofMillis(value));
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

	/** @param ownerId the scene or revision the value belongs to */
	private record ValueRow(long ownerId, long fixtureId, Attribute attribute, double value) {
	}
}
