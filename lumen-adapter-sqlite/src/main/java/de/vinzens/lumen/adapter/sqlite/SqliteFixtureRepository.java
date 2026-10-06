package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.project.port.out.FixtureRepository;
import de.vinzens.lumen.domain.project.FixtureSpec;
import de.vinzens.lumen.domain.project.PatchedFixture;
import de.vinzens.lumen.domain.project.Position;
import de.vinzens.lumen.domain.project.StageShape;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.List;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteFixtureRepository implements FixtureRepository {

	private static final String SELECT = "SELECT * FROM fixture";

	private static final RowMapper<PatchedFixture> MAPPER = (rs, row) -> {
		// the SQLite driver throws on getObject(…, Double.class) for NULL, hence getDouble + wasNull
		var x = rs.getDouble("pos_x");
		var hasX = !rs.wasNull();
		var y = rs.getDouble("pos_y");
		var hasY = !rs.wasNull();
		return new PatchedFixture(
				rs.getLong("id"),
				rs.getLong("project_id"),
				new FixtureSpec(
						rs.getString("name"),
						Optional.ofNullable(rs.getString("short_name")),
						rs.getString("fixture_type_id"),
						rs.getString("mode_name"),
						rs.getLong("universe_id"),
						rs.getInt("address"),
						hasX && hasY ? Optional.of(new Position(x, y)) : Optional.empty(),
						rs.getInt("sort_order"),
						new StageShape(
								StageShape.Kind.fromId(rs.getString("stage_shape")).orElse(StageShape.Kind.AUTO),
								rs.getInt("stage_rotation"),
								rs.getDouble("stage_length"))));
	};

	private final JdbcClient jdbc;

	public SqliteFixtureRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PatchedFixture> findByProject(long projectId) {
		return jdbc.sql(SELECT + " WHERE project_id = ? ORDER BY sort_order, name COLLATE NOCASE, id")
				.param(projectId).query(MAPPER).list();
	}

	@Override
	public Optional<PatchedFixture> findById(long id) {
		return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(MAPPER).optional();
	}

	@Override
	public PatchedFixture create(long projectId, FixtureSpec spec) {
		var keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO fixture (project_id, name, short_name, fixture_type_id, mode_name, universe_id, address,
				    pos_x, pos_y, sort_order, stage_shape, stage_rotation, stage_length)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(projectId, spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(), spec.modeName(),
						spec.universeId(), spec.address(), x(spec), y(spec), spec.sortOrder(),
						spec.stageShape().kind().id(), spec.stageShape().rotation(), spec.stageShape().length())
				.update(keys);
		return findById(generatedId(keys)).orElseThrow();
	}

	@Override
	public PatchedFixture update(long id, FixtureSpec spec) {
		jdbc.sql("""
				UPDATE fixture SET name = ?, short_name = ?, fixture_type_id = ?, mode_name = ?, universe_id = ?,
				    address = ?, pos_x = ?, pos_y = ?, sort_order = ?, stage_shape = ?, stage_rotation = ?,
				    stage_length = ?
				WHERE id = ?""")
				.params(spec.name(), spec.shortName().orElse(null), spec.fixtureTypeId(), spec.modeName(),
						spec.universeId(), spec.address(), x(spec), y(spec), spec.sortOrder(),
						spec.stageShape().kind().id(), spec.stageShape().rotation(), spec.stageShape().length(), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM fixture WHERE id = ?").param(id).update();
	}

	@Override
	public int countByUniverse(long universeId) {
		return jdbc.sql("SELECT COUNT(*) FROM fixture WHERE universe_id = ?").param(universeId)
				.query(Integer.class).single();
	}

	private static Double x(FixtureSpec spec) {
		return spec.position().map(Position::x).orElse(null);
	}

	private static Double y(FixtureSpec spec) {
		return spec.position().map(Position::y).orElse(null);
	}
}
