package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.output.port.out.ExpectedNodeRepository;
import de.vinzens.lumen.domain.project.ExpectedNode;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class SqliteExpectedNodeRepository implements ExpectedNodeRepository {

	private final JdbcClient jdbc;

	public SqliteExpectedNodeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ExpectedNode> findByProject(long projectId) {
		return jdbc.sql("SELECT ip_address, name FROM expected_node WHERE project_id = ? ORDER BY ip_address")
				.param(projectId)
				.query((rs, row) -> new ExpectedNode(rs.getString("ip_address"), rs.getString("name")))
				.list();
	}

	@Override
	public void save(long projectId, ExpectedNode node) {
		jdbc.sql("""
				INSERT INTO expected_node (project_id, ip_address, name) VALUES (?, ?, ?)
				ON CONFLICT (project_id, ip_address) DO UPDATE SET name = excluded.name""")
				.params(projectId, node.ipAddress(), node.name())
				.update();
	}

	@Override
	public void delete(long projectId, String ipAddress) {
		jdbc.sql("DELETE FROM expected_node WHERE project_id = ? AND ip_address = ?")
				.params(projectId, ipAddress)
				.update();
	}
}
