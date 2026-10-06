package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.legacy.LegacyShow;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyComposition;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyKey;
import de.vinzens.lumen.application.legacy.LegacyShow.LegacyScene;
import de.vinzens.lumen.application.legacy.port.out.LegacyShowStore;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads the show data of the model before iteration S, which migration V19 kept in the {@code legacy_*} tables.
 */
public class SqliteLegacyShowStore implements LegacyShowStore {

	private final JdbcClient jdbc;
	private final LookTables lookTables;
	private final SqliteSceneRepository scenes;
	private final PatternCodec codec = new PatternCodec();

	public SqliteLegacyShowStore(JdbcClient jdbc, SqliteSceneRepository scenes) {
		this.jdbc = jdbc;
		this.lookTables = new LookTables(jdbc);
		this.scenes = scenes;
	}

	@Override
	public List<Long> pendingProjects() {
		return jdbc.sql("""
				SELECT id FROM project p
				WHERE NOT EXISTS (SELECT 1 FROM legacy_conversion c WHERE c.project_id = p.id)
				  AND (EXISTS (SELECT 1 FROM legacy_scene_value v JOIN scene s ON s.id = v.scene_id
				               WHERE s.project_id = p.id)
				    OR EXISTS (SELECT 1 FROM legacy_composition c WHERE c.project_id = p.id)
				    OR EXISTS (SELECT 1 FROM legacy_extra_key_scene k WHERE k.project_id = p.id))
				ORDER BY id""").query(Long.class).list();
	}

	@Override
	public LegacyShow load(long projectId) {
		var looks = lookTables.load("legacy_scene", "scene_id",
				"scene_id IN (SELECT id FROM scene WHERE project_id = ?)", projectId);
		var legacyScenes = scenes.findByProject(projectId).stream()
				.map(scene -> new LegacyScene(scene.id(), scene.spec(), looks.getOrDefault(scene.id(), Look.EMPTY)))
				.toList();
		var rows = jdbc.sql("SELECT * FROM legacy_composition WHERE project_id = ? ORDER BY id").param(projectId)
				.query(this::row).list();
		var compositions = new ArrayList<LegacyComposition>();
		var keys = new ArrayList<LegacyKey>();
		for (var row : rows) {
			var composition = toComposition(row);
			if (row.keySlot() != null) {
				keys.add(new LegacyKey(row.keySlot(), keyMode(row.keyMode()),
						Duration.ofMillis(row.keyFadeMs() == null ? 0 : row.keyFadeMs()), Optional.of(composition),
						Optional.empty()));
			} else {
				compositions.add(composition);
			}
		}
		jdbc.sql("SELECT * FROM legacy_extra_key_scene WHERE project_id = ?").param(projectId).query(rs -> {
			keys.add(new LegacyKey(rs.getInt("key_slot"), keyMode(rs.getString("key_mode")),
					Duration.ofMillis(rs.getLong("key_fade_ms")), Optional.empty(),
					Optional.of(rs.getLong("scene_id"))));
		});
		var sceneKeys = new HashMap<Long, Set<Integer>>();
		jdbc.sql("""
				SELECT k.scene_id, k.key_slot FROM legacy_scene_extra_key k JOIN scene s ON s.id = k.scene_id
				WHERE s.project_id = ?""").param(projectId).query(rs -> {
			sceneKeys.computeIfAbsent(rs.getLong("scene_id"), id -> new TreeSet<>()).add(rs.getInt("key_slot"));
		});
		return new LegacyShow(legacyScenes, compositions, keys, sceneKeys);
	}

	@Override
	public List<Revision> revisions(long sceneId) {
		var looks = lookTables.load("legacy_scene_revision", "revision_id",
				"revision_id IN (SELECT id FROM legacy_scene_revision WHERE scene_id = ?)", sceneId);
		return jdbc.sql("SELECT * FROM legacy_scene_revision WHERE scene_id = ? ORDER BY id DESC").param(sceneId)
				.query((rs, n) -> new Revision(Instant.parse(rs.getString("created_at")), rs.getString("reason"),
						looks.getOrDefault(rs.getLong("id"), Look.EMPTY)))
				.list();
	}

	@Override
	public void markConverted(long projectId, Instant at) {
		jdbc.sql("INSERT OR REPLACE INTO legacy_conversion (project_id, converted_at) VALUES (?, ?)")
				.params(projectId, at.toString()).update();
	}

	private LegacyComposition toComposition(Row row) {
		var kind = PatternKind.fromId(row.kind());
		var steps = kind == PatternKind.STEPS;
		var pattern = steps
				? new StepSequence(steps(row.id()), codec.legacyPlayMode(row.params()))
				: codec.read(kind, row.params());
		Optional<Target> target = switch (row.target()) {
			case "group" -> Optional.of(row.groupId() != null ? new Target.Group(row.groupId())
					: new Target.Fixtures(List.of()));
			case "fixtures" -> Optional.of(new Target.Fixtures(jdbc.sql(
					"SELECT fixture_id FROM legacy_composition_target WHERE composition_id = ? ORDER BY position")
					.param(row.id()).query(Long.class).list()));
			default -> Optional.empty();
		};
		return new LegacyComposition(row.id(), Optional.ofNullable(row.sceneId()), Optional.ofNullable(row.sourceId()),
				row.position(), row.name(), pattern, target, row.level(), row.speedFactor(), row.restart(), row.once(),
				row.perHead());
	}

	private List<StepSequence.Step> steps(long compositionId) {
		var values = new TreeMap<Integer, Map<Long, Map<Attribute, Double>>>();
		jdbc.sql("SELECT * FROM legacy_composition_step_value WHERE composition_id = ?").param(compositionId)
				.query(rs -> {
					values.computeIfAbsent(rs.getInt("step_index"), i -> new HashMap<>())
							.computeIfAbsent(rs.getLong("fixture_id"), f -> new HashMap<>())
							.put(Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
				});
		return jdbc.sql("SELECT * FROM legacy_composition_step WHERE composition_id = ? ORDER BY step_index")
				.param(compositionId)
				.query((rs, n) -> {
					var perFixture = new HashMap<Long, FixtureValues>();
					values.getOrDefault(rs.getInt("step_index"), Map.of())
							.forEach((fixtureId, attributes) -> perFixture.put(fixtureId, new FixtureValues(attributes)));
					return new StepSequence.Step(rs.getLong("hold_ms") / 1000.0, rs.getLong("fade_ms") / 1000.0,
							perFixture);
				})
				.list();
	}

	private static KeyMode keyMode(String id) {
		return KeyMode.valueOf(id.toUpperCase(Locale.ROOT));
	}

	private Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getLong("id"), nullableLong(rs, "scene_id"), nullableLong(rs, "source_id"),
				rs.getString("name"), rs.getString("kind"), rs.getString("params"), rs.getString("target"),
				nullableLong(rs, "group_id"), rs.getInt("position"), rs.getDouble("level"), rs.getDouble("speed_factor"),
				rs.getInt("restart") == 1, rs.getInt("once") == 1, rs.getInt("per_head") == 1,
				nullableInt(rs, "key_slot"), rs.getString("key_mode"), nullableLong(rs, "key_fade_ms"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		var value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private record Row(long id, Long sceneId, Long sourceId, String name, String kind, String params, String target,
			Long groupId, int position, double level, double speedFactor, boolean restart, boolean once,
			boolean perHead, Integer keySlot, String keyMode, Long keyFadeMs) {
	}
}
