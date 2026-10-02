package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.project.port.out.GroupRepository;
import de.vinzens.lumen.domain.project.Group;
import de.vinzens.lumen.domain.project.GroupSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteGroupRepository implements GroupRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;

	public SqliteGroupRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public List<Group> findByProject(long projectId) {
		return jdbc.sql("SELECT * FROM fixture_group WHERE project_id = ? ORDER BY sort_order, name COLLATE NOCASE, id")
				.param(projectId)
				.query((rs, row) -> rs.getLong("id"))
				.list().stream()
				.map(id -> findById(id).orElseThrow())
				.toList();
	}

	@Override
	public Optional<Group> findById(long id) {
		return jdbc.sql("SELECT * FROM fixture_group WHERE id = ?").param(id)
				.query((rs, row) -> new GroupRow(rs.getLong("project_id"), rs.getString("name"),
						Optional.ofNullable(rs.getString("color")), rs.getInt("sort_order")))
				.optional()
				.map(row -> new Group(id, row.projectId(),
						new GroupSpec(row.name(), row.color(), row.sortOrder(), members(id))));
	}

	@Override
	public Group create(long projectId, GroupSpec spec) {
		var id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO fixture_group (project_id, name, color, sort_order) VALUES (?, ?, ?, ?)")
					.params(projectId, spec.name(), spec.color().orElse(null), spec.sortOrder())
					.update(keys);
			var groupId = generatedId(keys);
			insertMembers(groupId, spec.fixtureIds());
			return groupId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public Group update(long id, GroupSpec spec) {
		transactions.executeWithoutResult(status -> {
			jdbc.sql("UPDATE fixture_group SET name = ?, color = ?, sort_order = ? WHERE id = ?")
					.params(spec.name(), spec.color().orElse(null), spec.sortOrder(), id)
					.update();
			jdbc.sql("DELETE FROM fixture_group_member WHERE group_id = ?").param(id).update();
			insertMembers(id, spec.fixtureIds());
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM fixture_group WHERE id = ?").param(id).update();
	}

	private List<Long> members(long groupId) {
		return jdbc.sql("SELECT fixture_id FROM fixture_group_member WHERE group_id = ? ORDER BY position")
				.param(groupId).query(Long.class).list();
	}

	private void insertMembers(long groupId, List<Long> fixtureIds) {
		for (int i = 0; i < fixtureIds.size(); i++) {
			jdbc.sql("INSERT INTO fixture_group_member (group_id, fixture_id, position) VALUES (?, ?, ?)")
					.params(groupId, fixtureIds.get(i), i)
					.update();
		}
	}

	private record GroupRow(long projectId, String name, Optional<String> color, int sortOrder) {
	}
}
