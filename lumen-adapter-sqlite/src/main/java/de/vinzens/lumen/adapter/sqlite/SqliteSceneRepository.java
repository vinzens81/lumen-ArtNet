package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.scene.port.out.SceneRepository;
import de.vinzens.lumen.domain.composition.KeyMode;
import de.vinzens.lumen.domain.scene.FadeCurve;
import de.vinzens.lumen.domain.scene.MoveInDark;
import de.vinzens.lumen.domain.scene.Scene;
import de.vinzens.lumen.domain.scene.SceneComposition;
import de.vinzens.lumen.domain.scene.SceneKey;
import de.vinzens.lumen.domain.scene.SceneLook;
import de.vinzens.lumen.domain.scene.SceneMove;
import de.vinzens.lumen.domain.scene.SceneNumber;
import de.vinzens.lumen.domain.scene.SceneSection;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

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
		var light = light(where, projectId);
		var keys = keys(where, projectId);
		return jdbc.sql("SELECT * FROM scene WHERE project_id = ?").param(projectId)
				.query((rs, n) -> scene(rs, light, keys)).list().stream()
				.sorted(Comparator.comparing(scene -> scene.spec().number()))
				.toList();
	}

	@Override
	public Optional<Scene> findById(long id) {
		var light = light("scene_id = ?", id);
		var keys = keys("scene_id = ?", id);
		return jdbc.sql("SELECT * FROM scene WHERE id = ?").param(id)
				.query((rs, n) -> scene(rs, light, keys)).optional();
	}

	@Override
	public Scene create(long projectId, SceneSpec spec, List<SceneComposition> compositions, List<SceneKey> keys) {
		long id = transactions.execute(status -> {
			var generated = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO scene (project_id, number, name, cue_note, auto_follow_ms, fade_in_ms, fade_out_ms,
					                   delay_ms, fade_curve, script, move_in_dark, dim_out_ms, travel_ms)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(projectId, spec.number().toString(), spec.name(), spec.cueNote().orElse(null),
							millis(spec.autoFollow()), millis(spec.timing().fadeIn()),
							millis(spec.timing().fadeOut()), spec.timing().delay().toMillis(), curve(spec),
							spec.script(), spec.timing().moveInDark().enabled() ? 1 : 0,
							millis(spec.timing().moveInDark().dimOut()), millis(spec.timing().moveInDark().travel()))
					.update(generated);
			var sceneId = generatedId(generated);
			insertCompositions(sceneId, "", compositions);
			insertKeys(sceneId, keys);
			return sceneId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene updateSpec(long id, SceneSpec spec) {
		jdbc.sql("""
				UPDATE scene SET number = ?, name = ?, cue_note = ?, auto_follow_ms = ?, fade_in_ms = ?,
				                 fade_out_ms = ?, delay_ms = ?, fade_curve = ?, script = ?, move_in_dark = ?,
				                 dim_out_ms = ?, travel_ms = ?
				WHERE id = ?""")
				.params(spec.number().toString(), spec.name(), spec.cueNote().orElse(null), millis(spec.autoFollow()),
						millis(spec.timing().fadeIn()), millis(spec.timing().fadeOut()),
						spec.timing().delay().toMillis(), curve(spec), spec.script(),
						spec.timing().moveInDark().enabled() ? 1 : 0, millis(spec.timing().moveInDark().dimOut()),
						millis(spec.timing().moveInDark().travel()), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public Scene replaceCompositions(long id, List<SceneComposition> compositions) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_composition WHERE scene_id = ? AND section = ''").param(id).update();
			insertCompositions(id, "", compositions);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Scene replaceSection(long id, String marker, List<SceneComposition> compositions, List<SceneLook> looks,
			List<SceneMove> moves) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM scene_composition WHERE scene_id = ? AND section = ?").params(id, marker).update();
			jdbc.sql("DELETE FROM scene_look WHERE scene_id = ? AND section = ?").params(id, marker).update();
			jdbc.sql("DELETE FROM scene_move WHERE scene_id = ? AND section = ?").params(id, marker).update();
			insertCompositions(id, marker, compositions);
			for (int i = 0; i < looks.size(); i++) {
				jdbc.sql("INSERT INTO scene_look (scene_id, section, look_id, position, level) VALUES (?, ?, ?, ?, ?)")
						.params(id, marker, looks.get(i).lookId(), i, looks.get(i).level()).update();
			}
			for (int i = 0; i < moves.size(); i++) {
				jdbc.sql("INSERT INTO scene_move (scene_id, section, move_id, position) VALUES (?, ?, ?, ?)")
						.params(id, marker, moves.get(i).moveId(), i).update();
			}
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

	private void insertCompositions(long sceneId, String section, List<SceneComposition> compositions) {
		for (int i = 0; i < compositions.size(); i++) {
			var use = compositions.get(i);
			jdbc.sql("""
					INSERT INTO scene_composition (scene_id, section, composition_id, position, level, speed_factor,
					                               restart, fade_in_ms, fade_out_ms)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(sceneId, section, use.compositionId(), i, use.level(), use.speedFactor(),
							use.restart() ? 1 : 0, millis(use.fadeIn()), millis(use.fadeOut()))
					.update();
		}
	}

	private void insertKeys(long sceneId, List<SceneKey> keys) {
		for (var key : keys) {
			jdbc.sql("""
					INSERT INTO scene_key (scene_id, slot, composition_id, move_id, key_mode, fade_ms)
					VALUES (?, ?, ?, ?, ?, ?)""")
					.params(sceneId, key.slot(), key.compositionId().orElse(null), key.moveId().orElse(null),
							key.mode().id(), key.fade().toMillis())
					.update();
		}
	}

	/** Compositions, looks and moves of a scene, per section ('' = the start of the scene). */
	private record Light(Map<String, List<SceneComposition>> compositions, Map<String, List<SceneLook>> looks,
			Map<String, List<SceneMove>> moves) {

		Light() {
			this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
		}
	}

	private Map<Long, Light> light(String where, long param) {
		var result = new HashMap<Long, Light>();
		jdbc.sql("SELECT * FROM scene_composition WHERE " + where + " ORDER BY scene_id, section, position")
				.param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new Light()).compositions()
							.computeIfAbsent(rs.getString("section"), s -> new ArrayList<>())
							.add(new SceneComposition(rs.getLong("composition_id"), rs.getDouble("level"),
									rs.getDouble("speed_factor"), rs.getInt("restart") == 1,
									optionalMillis(rs, "fade_in_ms"), optionalMillis(rs, "fade_out_ms")));
				});
		jdbc.sql("SELECT * FROM scene_look WHERE " + where + " ORDER BY scene_id, section, position").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new Light()).looks()
							.computeIfAbsent(rs.getString("section"), s -> new ArrayList<>())
							.add(new SceneLook(rs.getLong("look_id"), rs.getDouble("level")));
				});
		jdbc.sql("SELECT * FROM scene_move WHERE " + where + " ORDER BY scene_id, section, position").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new Light()).moves()
							.computeIfAbsent(rs.getString("section"), s -> new ArrayList<>())
							.add(new SceneMove(rs.getLong("move_id")));
				});
		return result;
	}

	private Map<Long, List<SceneKey>> keys(String where, long param) {
		var result = new HashMap<Long, List<SceneKey>>();
		jdbc.sql("SELECT * FROM scene_key WHERE " + where + " ORDER BY scene_id, slot").param(param)
				.query(rs -> {
					result.computeIfAbsent(rs.getLong("scene_id"), id -> new ArrayList<>()).add(new SceneKey(
							rs.getInt("slot"), optionalLong(rs, "composition_id"), optionalLong(rs, "move_id"),
							KeyMode.valueOf(rs.getString("key_mode").toUpperCase(Locale.ROOT)),
							Duration.ofMillis(rs.getLong("fade_ms"))));
				});
		return result;
	}

	private static Scene scene(ResultSet rs, Map<Long, Light> light, Map<Long, List<SceneKey>> keys)
			throws SQLException {
		var id = rs.getLong("id");
		var timing = new SceneTiming(optionalMillis(rs, "fade_in_ms"), optionalMillis(rs, "fade_out_ms"),
				Duration.ofMillis(rs.getLong("delay_ms")),
				Optional.ofNullable(rs.getString("fade_curve")).map(FadeCurve::parse),
				new MoveInDark(rs.getInt("move_in_dark") == 1, optionalMillis(rs, "dim_out_ms"),
						optionalMillis(rs, "travel_ms")));
		var spec = new SceneSpec(SceneNumber.parse(rs.getString("number")), rs.getString("name"),
				Optional.ofNullable(rs.getString("cue_note")), optionalMillis(rs, "auto_follow_ms"),
				timing, rs.getString("script"));
		var own = light.getOrDefault(id, new Light());
		var markers = new TreeSet<String>(own.compositions().keySet());
		markers.addAll(own.looks().keySet());
		markers.addAll(own.moves().keySet());
		markers.remove("");
		var sections = markers.stream()
				.map(marker -> new SceneSection(marker, own.compositions().getOrDefault(marker, List.of()),
						own.looks().getOrDefault(marker, List.of()), own.moves().getOrDefault(marker, List.of())))
				.toList();
		return new Scene(id, rs.getLong("project_id"), spec, own.compositions().getOrDefault("", List.of()),
				keys.getOrDefault(id, List.of()), own.looks().getOrDefault("", List.of()),
				own.moves().getOrDefault("", List.of()), sections);
	}

	private static String curve(SceneSpec spec) {
		return spec.timing().curve().map(FadeCurve::id).orElse(null);
	}

	private static Long millis(Optional<Duration> duration) {
		return duration.map(Duration::toMillis).orElse(null);
	}

	private static Optional<Long> optionalLong(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(value);
	}

	private static Optional<Duration> optionalMillis(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(Duration.ofMillis(value));
	}
}
