package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.domain.composition.Chase;
import de.vinzens.lumen.domain.composition.ColorCycle;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.ExtraKey;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.composition.Flicker;
import de.vinzens.lumen.domain.composition.Pattern;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.Rainbow;
import de.vinzens.lumen.domain.composition.StepSequence;
import de.vinzens.lumen.domain.composition.StepSequence.Step;
import de.vinzens.lumen.domain.composition.Target;
import de.vinzens.lumen.domain.composition.Wave;
import de.vinzens.lumen.domain.library.Attribute;
import de.vinzens.lumen.domain.output.FixtureValues;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.time.Duration;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

/**
 * Compositions with their targets and steps. Pattern parameters are stored as JSON (the record components of the
 * pattern); steps of a step sequence live in their own tables.
 */
public class SqliteCompositionRepository implements CompositionRepository {

	private static final Map<PatternKind, Class<? extends Pattern>> TYPES = Map.of(
			PatternKind.CHASE, Chase.class,
			PatternKind.COLOR_CYCLE, ColorCycle.class,
			PatternKind.RAINBOW, Rainbow.class,
			PatternKind.WAVE, Wave.class,
			PatternKind.FLICKER, Flicker.class);

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;
	private final JsonMapper json = JsonMapper.builder().build();

	public SqliteCompositionRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Composition> findLibrary(long projectId) {
		return load(jdbc.sql("""
				SELECT * FROM composition WHERE project_id = ? AND scene_id IS NULL AND key_slot IS NULL
				ORDER BY name COLLATE NOCASE, id""").param(projectId).query(this::row).list());
	}

	@Override
	public List<Composition> findByScene(long sceneId) {
		return load(jdbc.sql("SELECT * FROM composition WHERE scene_id = ? ORDER BY position, id")
				.param(sceneId).query(this::row).list());
	}

	@Override
	public Map<Long, List<Composition>> findByScenes(long projectId) {
		var compositions = load(jdbc.sql("""
				SELECT * FROM composition WHERE project_id = ? AND scene_id IS NOT NULL ORDER BY scene_id, position, id""")
				.param(projectId).query(this::row).list());
		var result = new LinkedHashMap<Long, List<Composition>>();
		compositions.forEach(c -> result.computeIfAbsent(c.sceneId().orElseThrow(), id -> new ArrayList<>()).add(c));
		return result;
	}

	@Override
	public Optional<Composition> findById(long id) {
		return load(jdbc.sql("SELECT * FROM composition WHERE id = ?").param(id).query(this::row).list())
				.stream().findFirst();
	}

	@Override
	public Composition create(long projectId, Optional<Long> sceneId, Optional<Long> sourceId, CompositionSpec spec) {
		long id = transactions.execute(status -> {
			var position = sceneId.map(scene -> jdbc.sql(
					"SELECT COALESCE(MAX(position) + 1, 0) FROM composition WHERE scene_id = ?")
					.param(scene).query(Integer.class).single()).orElse(0);
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO composition (project_id, scene_id, source_id, name, kind, params, target, group_id,
					    position, level, speed_factor, restart, once, per_head)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(projectId, sceneId.orElse(null), sourceId.orElse(null), spec.name(),
							spec.pattern().kind().id(), params(spec.pattern()), targetKind(spec.target()),
							groupId(spec.target()), position, spec.level(), spec.speedFactor(), spec.restart() ? 1 : 0,
							spec.once() ? 1 : 0, spec.perHead() ? 1 : 0)
					.update(keys);
			var compositionId = generatedId(keys);
			insertDetails(compositionId, spec);
			return compositionId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Composition update(long id, CompositionSpec spec) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("""
					UPDATE composition SET name = ?, kind = ?, params = ?, target = ?, group_id = ?, level = ?,
					    speed_factor = ?, restart = ?, once = ?, per_head = ?
					WHERE id = ?""")
					.params(spec.name(), spec.pattern().kind().id(), params(spec.pattern()), targetKind(spec.target()),
							groupId(spec.target()), spec.level(), spec.speedFactor(), spec.restart() ? 1 : 0,
							spec.once() ? 1 : 0, spec.perHead() ? 1 : 0, id)
					.update();
			jdbc.sql("DELETE FROM composition_target WHERE composition_id = ?").param(id).update();
			jdbc.sql("DELETE FROM composition_step WHERE composition_id = ?").param(id).update();
			insertDetails(id, spec);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM composition WHERE id = ?").param(id).update();
	}

	@Override
	public void reorder(long sceneId, List<Long> ids) {
		transactions.executeWithoutResult(status -> {
			for (int i = 0; i < ids.size(); i++) {
				jdbc.sql("UPDATE composition SET position = ? WHERE id = ? AND scene_id = ?")
						.params(i, ids.get(i), sceneId).update();
			}
		});
	}

	// --- extra keys --------------------------------------------------------------------------------------------

	@Override
	public List<ExtraKey> findKeys(long projectId) {
		var keys = new ArrayList<ExtraKey>();
		jdbc.sql("SELECT * FROM composition WHERE project_id = ? AND key_slot IS NOT NULL").param(projectId)
				.query(this::row).list()
				.forEach(row -> keys.add(new ExtraKey(row.keySlot(), keyMode(row.keyMode()),
						Duration.ofMillis(row.keyFadeMs() == null ? 0 : row.keyFadeMs()), toComposition(row))));
		jdbc.sql("SELECT * FROM extra_key_scene WHERE project_id = ?").param(projectId)
				.query(rs -> {
					keys.add(new ExtraKey(rs.getInt("key_slot"), keyMode(rs.getString("key_mode")),
							Duration.ofMillis(rs.getLong("key_fade_ms")), new ExtraKey.Content.PlaysScene(
									rs.getLong("scene_id"))));
				});
		keys.sort(Comparator.comparingInt(ExtraKey::slot));
		return keys;
	}

	@Override
	public ExtraKey saveSceneKey(long projectId, int slot, long sceneId, KeyMode mode, Duration fade) {
		transactions.executeWithoutResult(status -> {
			deleteKey(projectId, slot);
			jdbc.sql("""
					INSERT INTO extra_key_scene (project_id, key_slot, scene_id, key_mode, key_fade_ms)
					VALUES (?, ?, ?, ?, ?)""")
					.params(projectId, slot, sceneId, mode.id(), fade.toMillis()).update();
		});
		return key(projectId, slot);
	}

	private static KeyMode keyMode(String id) {
		return KeyMode.valueOf(id.toUpperCase(Locale.ROOT));
	}

	@Override
	public ExtraKey saveKey(long projectId, int slot, Optional<Long> sourceId, KeyMode mode, Duration fade,
			CompositionSpec spec) {
		transactions.executeWithoutResult(status -> {
			deleteKey(projectId, slot);
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO composition (project_id, key_slot, key_mode, key_fade_ms, source_id, name, kind, params,
					    target, group_id, level, speed_factor, restart, once, per_head)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(projectId, slot, mode.id(), fade.toMillis(), sourceId.orElse(null), spec.name(),
							spec.pattern().kind().id(), params(spec.pattern()), targetKind(spec.target()),
							groupId(spec.target()), spec.level(), spec.speedFactor(), spec.restart() ? 1 : 0,
							spec.once() ? 1 : 0, spec.perHead() ? 1 : 0)
					.update(keys);
			insertDetails(generatedId(keys), spec);
		});
		return key(projectId, slot);
	}

	@Override
	public ExtraKey updateKeySettings(long projectId, int slot, KeyMode mode, Duration fade) {
		jdbc.sql("UPDATE composition SET key_mode = ?, key_fade_ms = ? WHERE project_id = ? AND key_slot = ?")
				.params(mode.id(), fade.toMillis(), projectId, slot).update();
		jdbc.sql("UPDATE extra_key_scene SET key_mode = ?, key_fade_ms = ? WHERE project_id = ? AND key_slot = ?")
				.params(mode.id(), fade.toMillis(), projectId, slot).update();
		return key(projectId, slot);
	}

	@Override
	public void deleteKey(long projectId, int slot) {
		jdbc.sql("DELETE FROM composition WHERE project_id = ? AND key_slot = ?").params(projectId, slot).update();
		jdbc.sql("DELETE FROM extra_key_scene WHERE project_id = ? AND key_slot = ?").params(projectId, slot).update();
	}

	@Override
	public Map<Long, Set<Integer>> findSceneKeys(long projectId) {
		var result = new HashMap<Long, Set<Integer>>();
		jdbc.sql("""
				SELECT k.scene_id, k.key_slot FROM scene_extra_key k JOIN scene s ON s.id = k.scene_id
				WHERE s.project_id = ?""")
				.param(projectId)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new TreeSet<>()).add(rs.getInt("key_slot"));
				});
		return result;
	}

	@Override
	public void saveSceneKeys(long sceneId, Set<Integer> slots) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_extra_key WHERE scene_id = ?").param(sceneId).update();
			for (var slot : slots) {
				jdbc.sql("INSERT INTO scene_extra_key (scene_id, key_slot) VALUES (?, ?)").params(sceneId, slot).update();
			}
		});
	}

	private ExtraKey key(long projectId, int slot) {
		return findKeys(projectId).stream().filter(k -> k.slot() == slot).findFirst().orElseThrow();
	}

	// --- writing ---------------------------------------------------------------------------------------------

	private String params(Pattern pattern) {
		if (pattern instanceof StepSequence steps) {
			return json.writeValueAsString(Map.of("playMode", steps.playMode().name()));
		}
		return json.writeValueAsString(pattern);
	}

	private static String targetKind(Target target) {
		return switch (target) {
			case Target.Group group -> "group";
			case Target.Fixtures fixtures -> "fixtures";
			case Target.Steps steps -> "steps";
		};
	}

	private static Long groupId(Target target) {
		return target instanceof Target.Group group ? group.groupId() : null;
	}

	private void insertDetails(long compositionId, CompositionSpec spec) {
		if (spec.target() instanceof Target.Fixtures fixtures) {
			for (int i = 0; i < fixtures.fixtureIds().size(); i++) {
				jdbc.sql("INSERT INTO composition_target (composition_id, fixture_id, position) VALUES (?, ?, ?)")
						.params(compositionId, fixtures.fixtureIds().get(i), i).update();
			}
		}
		if (spec.pattern() instanceof StepSequence sequence) {
			for (int i = 0; i < sequence.steps().size(); i++) {
				var step = sequence.steps().get(i);
				jdbc.sql("INSERT INTO composition_step (composition_id, step_index, hold_ms, fade_ms) VALUES (?, ?, ?, ?)")
						.params(compositionId, i, Math.round(step.holdSeconds() * 1000),
								Math.round(step.fadeSeconds() * 1000))
						.update();
				var index = i;
				step.values().forEach((fixtureId, values) -> values.values().forEach((attribute, value) ->
						jdbc.sql("""
								INSERT INTO composition_step_value (composition_id, step_index, fixture_id, attribute, value)
								VALUES (?, ?, ?, ?, ?)""")
								.params(compositionId, index, fixtureId, attribute.id(), value).update()));
			}
		}
	}

	// --- reading ---------------------------------------------------------------------------------------------

	private List<Composition> load(List<Row> rows) {
		return rows.stream().map(this::toComposition).toList();
	}

	private Composition toComposition(Row row) {
		var kind = PatternKind.fromId(row.kind());
		var pattern = kind == PatternKind.STEPS
				? new StepSequence(steps(row.id()), StepSequence.PlayMode.valueOf(
						json.readTree(row.params()).get("playMode").asString()))
				: json.readValue(row.params(), TYPES.get(kind));
		Target target = switch (row.target()) {
			case "group" -> row.groupId() != null ? new Target.Group(row.groupId()) : new Target.Fixtures(List.of());
			case "fixtures" -> new Target.Fixtures(jdbc.sql(
					"SELECT fixture_id FROM composition_target WHERE composition_id = ? ORDER BY position")
					.param(row.id()).query(Long.class).list());
			default -> new Target.Steps();
		};
		return new Composition(row.id(), row.projectId(), Optional.ofNullable(row.sceneId()),
				Optional.ofNullable(row.sourceId()), row.position(),
				new CompositionSpec(row.name(), pattern, target, row.level(), row.speedFactor(), row.restart(),
						row.once(), row.perHead()));
	}

	private List<Step> steps(long compositionId) {
		var values = new TreeMap<Integer, Map<Long, Map<Attribute, Double>>>();
		jdbc.sql("SELECT * FROM composition_step_value WHERE composition_id = ?").param(compositionId)
				.query(rs -> {
					values.computeIfAbsent(rs.getInt("step_index"), i -> new HashMap<>())
							.computeIfAbsent(rs.getLong("fixture_id"), f -> new HashMap<>())
							.put(Attribute.parse(rs.getString("attribute")), rs.getDouble("value"));
				});
		return jdbc.sql("SELECT * FROM composition_step WHERE composition_id = ? ORDER BY step_index")
				.param(compositionId)
				.query((rs, n) -> {
					var perFixture = new HashMap<Long, FixtureValues>();
					values.getOrDefault(rs.getInt("step_index"), Map.of())
							.forEach((fixtureId, attributes) -> perFixture.put(fixtureId, new FixtureValues(attributes)));
					return new Step(rs.getLong("hold_ms") / 1000.0, rs.getLong("fade_ms") / 1000.0, perFixture);
				})
				.list();
	}

	private Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getLong("id"), rs.getLong("project_id"), nullableLong(rs, "scene_id"),
				nullableLong(rs, "source_id"), rs.getString("name"), rs.getString("kind"), rs.getString("params"),
				rs.getString("target"), nullableLong(rs, "group_id"), rs.getInt("position"), rs.getDouble("level"),
				rs.getDouble("speed_factor"), rs.getInt("restart") == 1, rs.getInt("once") == 1,
				rs.getInt("per_head") == 1, nullableInt(rs, "key_slot"), rs.getString("key_mode"), nullableLong(rs, "key_fade_ms"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		var value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private record Row(long id, long projectId, Long sceneId, Long sourceId, String name, String kind, String params,
			String target, Long groupId, int position, double level, double speedFactor, boolean restart,
			boolean once, boolean perHead, Integer keySlot, String keyMode, Long keyFadeMs) {
	}
}
