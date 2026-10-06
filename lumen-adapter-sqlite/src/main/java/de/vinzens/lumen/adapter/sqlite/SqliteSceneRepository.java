package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSpec;
import de.vinzens.lumen.domain.scene.SceneTiming;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

/** Scenes with the compositions they play and their extra keys. */
public class SqliteSceneRepository implements SceneRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteSceneRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Scene> findByProject(long projectId) {
		var where = "scene_id IN (SELECT id FROM scene WHERE project_id = ?)";
		var compositions = compositions(where, projectId);
		var keys = keys(where, projectId);
		return jdbc.sql("SELECT * FROM scene WHERE project_id = ?").param(projectId)
				.query((rs, n) -> scene(rs, compositions, keys)).list().stream()
				.sorted(Comparator.comparing(scene -> scene.spec().number()))
				.toList();
	}

	@Override
	public Optional<Scene> findById(long id) {
		var compositions = compositions("scene_id = ?", id);
		var keys = keys("scene_id = ?", id);
		return jdbc.sql("SELECT * FROM scene WHERE id = ?").param(id)
				.query((rs, n) -> scene(rs, compositions, keys)).optional();
	}

	@Override
	public Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys) {
		long id = transactions.execute(status -> {
			var generated = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO scene (project_id, number, name, cue_note, auto_follow_ms, outside_cue_list, fade_in_ms,
					                   fade_out_ms, delay_ms, fade_curve, script)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(projectId, spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
							millis(spec.autoFollow()), spec.outsideCueList() ? 1 : 0, millis(spec.timing().fadeIn()),
							millis(spec.timing().fadeOut()), spec.timing().delay().toMillis(), curve(spec),
							spec.script())
					.update(generated);
			var sceneId = generatedId(generated);
			insertCompositions(sceneId, compositions);
			insertKeys(sceneId, keys);
			return sceneId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene updateSpec(long id, SceneSpec spec) {
		jdbc.sql("""
				UPDATE scene SET number = ?, name = ?, cue_note = ?, auto_follow_ms = ?, outside_cue_list = ?,
				                 fade_in_ms = ?, fade_out_ms = ?, delay_ms = ?, fade_curve = ?, script = ?
				WHERE id = ?""")
				.params(spec.number().toString(), spec.name(), spec.cueNote().orElse(null), millis(spec.autoFollow()),
						spec.outsideCueList() ? 1 : 0, millis(spec.timing().fadeIn()), millis(spec.timing().fadeOut()),
						spec.timing().delay().toMillis(), curve(spec), spec.script(), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public Scene replaceCompositions(long id, List<SceneComposition> compositions) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_composition WHERE scene_id = ?").param(id).update();
			insertCompositions(id, compositions);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene replaceKeys(long id, List<SceneKey> keys) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_key WHERE scene_id = ?").param(id).update();
			insertKeys(id, keys);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM scene WHERE id = ?").param(id).update();
	}

	private void insertCompositions(long sceneId, List<SceneComposition> compositions) {
		for (int i = 0; i < compositions.size(); i++) {
			var use = compositions.get(i);
			jdbc.sql("""
					INSERT INTO scene_composition (scene_id, composition_id, position, level, speed_factor, restart)
					VALUES (?, ?, ?, ?, ?, ?)""")
					.params(sceneId, use.compositionId(), i, use.level(), use.speedFactor(), use.restart() ? 1 : 0)
					.update();
		}
	}

	private void insertKeys(long sceneId, List<SceneKey> keys) {
		for (var key : keys) {
			jdbc.sql("""
					INSERT INTO scene_key (scene_id, slot, composition_id, key_mode, fade_ms) VALUES (?, ?, ?, ?, ?)""")
					.params(sceneId, key.slot(), key.compositionId(), key.mode().id(), key.fade().toMillis())
					.update();
		}
	}

	private Map<Long, List<SceneComposition>> compositions(String where, long param) {
		var result = new HashMap<Long, List<SceneComposition>>();
		jdbc.sql("SELECT * FROM scene_composition WHERE " + where + " ORDER BY scene_id, position").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new ArrayList<>()).add(new SceneComposition(
							rs.getLong("composition_id"), rs.getDouble("level"), rs.getDouble("speed_factor"),
							rs.getInt("restart") == 1));
				});
		return result;
	}

	private Map<Long, List<SceneKey>> keys(String where, long param) {
		var result = new HashMap<Long, List<SceneKey>>();
		jdbc.sql("SELECT * FROM scene_key WHERE " + where + " ORDER BY scene_id, slot").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new ArrayList<>()).add(new SceneKey(
							rs.getInt("slot"), rs.getLong("composition_id"),
							KeyMode.valueOf(rs.getString("key_mode").toUpperCase(Locale.ROOT)),
							Duration.ofMillis(rs.getLong("fade_ms"))));
				});
		return result;
	}

	private static Scene scene(ResultSet rs, Map<Long, List<SceneComposition>> compositions,
			Map<Long, List<SceneKey>> keys) throws SQLException {
		var id = rs.getLong("id");
		var timing = new SceneTiming(optionalMillis(rs, "fade_in_ms"), optionalMillis(rs, "fade_out_ms"),
				Duration.ofMillis(rs.getLong("delay_ms")),
				Optional.ofNullable(rs.getString("fade_curve")).map(FadeCurve::parse));
		var spec = new SceneSpec(SceneNumber.parse(rs.getString("number")), rs.getString("name"),
				Optional.ofNullable(rs.getString("cue_note")), optionalMillis(rs, "auto_follow_ms"),
				rs.getInt("outside_cue_list") == 1, timing, rs.getString("script"));
		return new Scene(id, rs.getLong("project_id"), spec, compositions.getOrDefault(id, List.of()),
				keys.getOrDefault(id, List.of()));
	}

	private static String curve(SceneSpec spec) {
		return spec.timing().curve().map(FadeCurve::id).orElse(null);
	}

	private static Long millis(Optional<Duration> duration) {
		return duration.map(Duration::toMillis).orElse(null);
	}

	private static Optional<Duration> optionalMillis(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(Duration.ofMillis(value));
	}
}
