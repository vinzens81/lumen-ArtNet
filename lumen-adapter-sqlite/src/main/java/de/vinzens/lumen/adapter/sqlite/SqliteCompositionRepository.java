package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.composition.port.out.CompositionRepository;
import de.vinzens.lumen.domain.composition.Composition;
import de.vinzens.lumen.domain.composition.CompositionSpec;
import de.vinzens.lumen.domain.composition.CompositionStep;
import de.vinzens.lumen.domain.composition.GeneratorSettings;
import de.vinzens.lumen.domain.composition.IntensityMode;
import de.vinzens.lumen.domain.composition.PatternKind;
import de.vinzens.lumen.domain.composition.PlayMode;
import de.vinzens.lumen.domain.composition.Target;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

/**
 * Compositions with their steps. The generator settings are stored as the effect's parameters (JSON) and its target.
 */
public class SqliteCompositionRepository implements CompositionRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;
	private final PatternCodec codec = new PatternCodec();

	public SqliteCompositionRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Composition> findByProject(long projectId) {
		var steps = steps("composition_id IN (SELECT id FROM composition WHERE project_id = ?)", projectId);
		var targets = targets("composition_id IN (SELECT id FROM composition WHERE project_id = ?)", projectId);
		return jdbc.sql("SELECT * FROM composition WHERE project_id = ? ORDER BY name COLLATE NOCASE, id")
				.param(projectId).query((rs, n) -> composition(rs, steps, targets)).list();
	}

	@Override
	public Optional<Composition> findById(long id) {
		var steps = steps("composition_id = ?", id);
		var targets = targets("composition_id = ?", id);
		return jdbc.sql("SELECT * FROM composition WHERE id = ?").param(id)
				.query((rs, n) -> composition(rs, steps, targets)).optional();
	}

	@Override
	public Composition create(long projectId, CompositionSpec spec, List<CompositionStep> steps) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO composition (project_id, name, fade_ms, hold_ms, play_mode, intensity_mode)
					VALUES (?, ?, 0, 0, 'loop', 'absolute')""")
					.params(projectId, spec.name())
					.update(keys);
			var compositionId = generatedId(keys);
			write(compositionId, spec, steps);
			return compositionId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Composition update(long id, CompositionSpec spec, List<CompositionStep> steps) {
		transactions.executeWithoutResult(status -> write(id, spec, steps));
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		transactions.executeWithoutResult(status -> {
			// steps first: the composition's own looks go with it and must not be played any more
			jdbc.sql("DELETE FROM composition_step WHERE composition_id = ?").param(id).update();
			jdbc.sql("DELETE FROM composition WHERE id = ?").param(id).update();
		});
	}

	private void write(long id, CompositionSpec spec, List<CompositionStep> steps) {
		var generator = spec.generator();
		jdbc.sql("""
				UPDATE composition SET name = ?, fade_ms = ?, hold_ms = ?, play_mode = ?, intensity_mode = ?,
				    generator_kind = ?, generator = ?, generator_group_id = ?, generator_per_head = ?
				WHERE id = ?""")
				.params(spec.name(), millis(spec.fadeSeconds()), millis(spec.holdSeconds()), spec.playMode().id(),
						spec.intensityMode().name().toLowerCase(Locale.ROOT),
						generator.map(g -> g.effect().kind().id()).orElse(null),
						generator.map(g -> codec.write(g.effect())).orElse(null),
						generator.map(g -> g.target() instanceof Target.Group group ? group.groupId() : null)
								.orElse(null),
						generator.map(g -> g.perHead() ? 1 : 0).orElse(0), id)
				.update();
		jdbc.sql("DELETE FROM composition_generator_fixture WHERE composition_id = ?").param(id).update();
		if (generator.isPresent() && generator.get().target() instanceof Target.Fixtures fixtures) {
			for (int i = 0; i < fixtures.fixtureIds().size(); i++) {
				jdbc.sql("""
						INSERT INTO composition_generator_fixture (composition_id, fixture_id, position)
						VALUES (?, ?, ?)""").params(id, fixtures.fixtureIds().get(i), i).update();
			}
		}
		jdbc.sql("DELETE FROM composition_step WHERE composition_id = ?").param(id).update();
		for (int i = 0; i < steps.size(); i++) {
			var step = steps.get(i);
			jdbc.sql("""
					INSERT INTO composition_step (composition_id, step_index, look_id, fade_ms, hold_ms, parallel)
					VALUES (?, ?, ?, ?, ?, ?)""")
					.params(id, i, step.lookId(), step.fadeSeconds().map(SqliteCompositionRepository::millis)
							.orElse(null), step.holdSeconds().map(SqliteCompositionRepository::millis).orElse(null),
							step.parallel() ? 1 : 0)
					.update();
		}
	}

	private Map<Long, List<CompositionStep>> steps(String where, long param) {
		var result = new HashMap<Long, List<CompositionStep>>();
		jdbc.sql("SELECT * FROM composition_step WHERE " + where + " ORDER BY composition_id, step_index")
				.param(param).query(rs -> {
					result.computeIfAbsent(rs.getLong("composition_id"), id -> new ArrayList<>()).add(
							new CompositionStep(rs.getLong("look_id"), seconds(rs, "fade_ms"), seconds(rs, "hold_ms"),
									rs.getInt("parallel") != 0));
				});
		return result;
	}

	/** Fixed fixture lists of generators. */
	private Map<Long, List<Long>> targets(String where, long param) {
		var result = new HashMap<Long, List<Long>>();
		jdbc.sql("SELECT * FROM composition_generator_fixture WHERE " + where + " ORDER BY composition_id, position")
				.param(param).query(rs -> {
					result.computeIfAbsent(rs.getLong("composition_id"), id -> new ArrayList<>())
							.add(rs.getLong("fixture_id"));
				});
		return result;
	}

	private Composition composition(ResultSet rs, Map<Long, List<CompositionStep>> steps,
			Map<Long, List<Long>> targets) throws SQLException {
		var id = rs.getLong("id");
		var kind = rs.getString("generator_kind");
		Optional<GeneratorSettings> generator = Optional.empty();
		if (kind != null) {
			var groupId = rs.getLong("generator_group_id");
			Target target = rs.wasNull() ? new Target.Fixtures(targets.getOrDefault(id, List.of()))
					: new Target.Group(groupId);
			generator = Optional.of(new GeneratorSettings(codec.read(PatternKind.fromId(kind), rs.getString("generator")),
					target, rs.getInt("generator_per_head") == 1));
		}
		var spec = new CompositionSpec(rs.getString("name"), rs.getLong("fade_ms") / 1000.0,
				rs.getLong("hold_ms") / 1000.0, PlayMode.fromId(rs.getString("play_mode")),
				IntensityMode.valueOf(rs.getString("intensity_mode").toUpperCase(Locale.ROOT)), generator);
		return new Composition(id, rs.getLong("project_id"), spec, steps.getOrDefault(id, List.of()));
	}

	private static Optional<Double> seconds(ResultSet rs, String column) throws SQLException {
		var value = rs.getLong(column);
		return rs.wasNull() ? Optional.empty() : Optional.of(value / 1000.0);
	}

	private static long millis(double seconds) {
		return Math.round(seconds * 1000);
	}
}
