package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.project.port.out.StagePlanRepository;
import de.vinzens.lumen.domain.project.StagePlan;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class SqliteStagePlanRepository implements StagePlanRepository {

	private final JdbcClient jdbc;

	public SqliteStagePlanRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<StagePlan> find(long projectId) {
		return jdbc.sql("SELECT content_type, data FROM stage_plan WHERE project_id = ?")
				.param(projectId)
				.query((rs, row) -> new StagePlan(rs.getString("content_type"), rs.getBytes("data")))
				.optional();
	}

	@Override
	public void save(long projectId, StagePlan plan) {
		jdbc.sql("""
				INSERT INTO stage_plan (project_id, content_type, data) VALUES (?, ?, ?)
				ON CONFLICT (project_id) DO UPDATE SET content_type = excluded.content_type, data = excluded.data""")
				.params(projectId, plan.contentType(), plan.data())
				.update();
	}

	@Override
	public void delete(long projectId) {
		jdbc.sql("DELETE FROM stage_plan WHERE project_id = ?").param(projectId).update();
	}
}
