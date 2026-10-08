package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.project.port.out.ProjectRepository;
import de.vinzens.lumen.domain.project.Project;
import de.vinzens.lumen.domain.project.ProjectSpec;
import de.vinzens.lumen.domain.scene.FadeCurve;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteProjectRepository implements ProjectRepository {

	private static final String SELECT = "SELECT * FROM project";

	private static final RowMapper<Project> MAPPER = (rs, row) -> new Project(
			rs.getLong("id"),
			new ProjectSpec(
					rs.getString("name"),
					Optional.ofNullable(rs.getString("venue")),
					Duration.ofMillis(rs.getLong("fade_in_ms")),
					Duration.ofMillis(rs.getLong("fade_out_ms")),
					FadeCurve.parse(rs.getString("fade_curve")),
					Duration.ofMillis(rs.getLong("dim_out_ms")),
					Duration.ofMillis(rs.getLong("travel_ms"))),
			rs.getBoolean("active"),
			Instant.parse(rs.getString("created_at")),
			Instant.parse(rs.getString("updated_at")));

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteProjectRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Project> findAll() {
		return jdbc.sql(SELECT + " ORDER BY name COLLATE NOCASE, id").query(MAPPER).list();
	}

	@Override
	public Optional<Project> findById(long id) {
		return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(MAPPER).optional();
	}

	@Override
	public Project create(ProjectSpec spec, Instant now) {
		var keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO project (name, venue, fade_in_ms, fade_out_ms, fade_curve, dim_out_ms, travel_ms, created_at,
				                     updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(), spec.fadeOut().toMillis(),
						spec.fadeCurve().id(), spec.dimOut().toMillis(), spec.travel().toMillis(), now.toString(),
						now.toString())
				.update(keys);
		return findById(generatedId(keys)).orElseThrow();
	}

	@Override
	public Project update(long id, ProjectSpec spec, Instant now) {
		jdbc.sql("""
				UPDATE project SET name = ?, venue = ?, fade_in_ms = ?, fade_out_ms = ?, fade_curve = ?, dim_out_ms = ?,
				                   travel_ms = ?, updated_at = ?
				WHERE id = ?""")
				.params(spec.name(), spec.venue().orElse(null), spec.fadeIn().toMillis(), spec.fadeOut().toMillis(),
						spec.fadeCurve().id(), spec.dimOut().toMillis(), spec.travel().toMillis(), now.toString(), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM project WHERE id = ?").param(id).update();
	}

	@Override
	public void activate(long id) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("UPDATE project SET active = 0 WHERE active = 1").update();
			jdbc.sql("UPDATE project SET active = 1 WHERE id = ?").param(id).update();
		});
	}
}
