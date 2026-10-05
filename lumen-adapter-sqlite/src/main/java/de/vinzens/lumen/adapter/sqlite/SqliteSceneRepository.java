package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.library.AttributeGroup;
import de.vinzens.lumen.domain.output.FixtureValues;
import de.vinzens.lumen.domain.output.PresetRefs;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.Look;
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
		var presets = presets("""
				SELECT p.scene_id AS owner_id, p.* FROM scene_preset p JOIN scene s ON s.id = p.scene_id
				WHERE s.project_id = ?""", projectId);
		return rows.stream()
				.map(row -> row.toScene(values.getOrDefault(row.id(), Map.of()),
						presets.getOrDefault(row.id(), PresetRefs.NONE)))
				.sorted(Comparator.comparing(scene -> scene.spec().number()))
				.toList();
	}

	@Override
	public Optional<Scene> findById(long id) {
		return jdbc.sql("SELECT * FROM scene WHERE id = ?").param(id).query(this::row).optional()
				.map(row -> row.toScene(valuesOfScene(id),
						presets("SELECT scene_id AS owner_id, * FROM scene_preset WHERE scene_id = ?", id)
								.getOrDefault(id, PresetRefs.NONE)));
	}

	@Override
	public Scene create(long projectId, SceneSpec spec, Look look) {
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
			insertLook(sceneId, look);
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
	public Scene replaceLook(long id, Look look) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_value WHERE scene_id = ?").param(id).update();
			jdbc.sql("DELETE FROM scene_preset WHERE scene_id = ?").param(id).update();
			insertLook(id, look);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM scene WHERE id = ?").param(id).update();
	}

	@Override
	public SceneRevision saveRevision(long sceneId, Instant createdAt, String reason, Look look, int keep) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO scene_revision (scene_id, created_at, reason) VALUES (?, ?, ?)")
					.params(sceneId, createdAt.toString(), reason)
					.update(keys);
			var revisionId = generatedId(keys);
			insertLook("scene_revision", "revision_id", revisionId, look);
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
		var presets = presets("""
				SELECT p.revision_id AS owner_id, p.* FROM scene_revision_preset p
				JOIN scene_revision r ON r.id = p.revision_id WHERE r.scene_id = ?""", sceneId);
		return jdbc.sql("SELECT * FROM scene_revision WHERE scene_id = ? ORDER BY id DESC").param(sceneId)
				.query((rs, rowNum) -> revision(rs, values, presets)).list();
	}

	@Override
	public Optional<SceneRevision> findRevision(long id) {
		var values = collect(jdbc.sql("SELECT * FROM scene_revision_value WHERE revision_id = ?")
				.param(id).query(this::revisionValueRow).list());
		var presets = presets("SELECT revision_id AS owner_id, * FROM scene_revision_preset WHERE revision_id = ?", id);
		return jdbc.sql("SELECT * FROM scene_revision WHERE id = ?").param(id)
				.query((rs, rowNum) -> revision(rs, values, presets)).optional();
	}

	private static SceneRevision revision(ResultSet rs, Map<Long, Map<Long, FixtureValues>> values,
			Map<Long, PresetRefs> presets) throws SQLException {
		var id = rs.getLong("id");
		return new SceneRevision(id, rs.getLong("scene_id"), Instant.parse(rs.getString("created_at")),
				rs.getString("reason"), new Look(values.getOrDefault(id, Map.of()),
						presets.getOrDefault(id, PresetRefs.NONE)));
	}

	/** Preset references per owner (scene or revision), from a query that names the owner {@code owner_id}. */
	private Map<Long, PresetRefs> presets(String sql, long param) {
		var raw = new HashMap<Long, Map<Long, Map<AttributeGroup, Long>>>();
		jdbc.sql(sql).param(param).query(rs -> {
			raw.computeIfAbsent(rs.getLong("owner_id"), id -> new HashMap<>())
					.computeIfAbsent(rs.getLong("fixture_id"), id -> new HashMap<>())
					.put(AttributeGroup.parse(rs.getString("attribute_group")), rs.getLong("preset_id"));
		});
		var result = new HashMap<Long, PresetRefs>();
		raw.forEach((ownerId, refs) -> result.put(ownerId, new PresetRefs(refs)));
		return result;
	}

	private void insertLook(long sceneId, Look look) {
		insertLook("scene", "scene_id", sceneId, look);
	}

	/** Inserts values and preset references into {@code <table>_value} and {@code <table>_preset}. */
	private void insertLook(String table, String ownerColumn, long ownerId, Look look) {
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

	private ValueRow revisionValueRow(ResultSet rs, int rowNum) throws SQLException {
		return new ValueRow(rs.getLong("revision_id"), rs.getLong("fixture_id"),
				Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
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

		Scene toScene(Map<Long, FixtureValues> values, PresetRefs presets) {
			return new Scene(id, projectId, spec, values, presets);
		}
	}

	/** @param ownerId the scene or revision the value belongs to */
	private record ValueRow(long ownerId, long fixtureId, Attribute attribute, double value) {
	}
}
