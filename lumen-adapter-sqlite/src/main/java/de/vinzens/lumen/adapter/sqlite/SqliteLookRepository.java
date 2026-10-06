package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.look.port.out.LookRepository;
import de.vinzens.lumen.domain.look.Look;
import de.vinzens.lumen.domain.look.LookRevision;
import de.vinzens.lumen.domain.look.LookSpec;
import de.vinzens.lumen.domain.look.NamedLook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteLookRepository implements LookRepository {

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;
	private final LookTables tables;

	public SqliteLookRepository(JdbcClient jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.tables = new LookTables(jdbc);
	}

	@Override
	public List<NamedLook> findByProject(long projectId) {
		var contents = tables.load("look", "look_id", "look_id IN (SELECT id FROM look WHERE project_id = ?)",
				projectId);
		return jdbc.sql("SELECT * FROM look WHERE project_id = ? ORDER BY name COLLATE NOCASE, id").param(projectId)
				.query((rs, n) -> look(rs, contents)).list();
	}

	@Override
	public Optional<NamedLook> findById(long id) {
		var contents = tables.load("look", "look_id", "look_id = ?", id);
		return jdbc.sql("SELECT * FROM look WHERE id = ?").param(id).query((rs, n) -> look(rs, contents)).optional();
	}

	@Override
	public NamedLook create(long projectId, LookSpec spec, Look look) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO look (project_id, name, owner_composition_id) VALUES (?, ?, ?)")
					.params(projectId, spec.name(), spec.owner().orElse(null))
					.update(keys);
			var lookId = generatedId(keys);
			tables.insert("look", "look_id", lookId, look);
			return lookId;
		});
		return findById(id).orElseThrow();
	}

	@Override
	public NamedLook updateSpec(long id, LookSpec spec) {
		jdbc.sql("UPDATE look SET name = ?, owner_composition_id = ? WHERE id = ?")
				.params(spec.name(), spec.owner().orElse(null), id).update();
		return findById(id).orElseThrow();
	}

	@Override
	public NamedLook replaceLook(long id, Look look) {
		transactions.executeWithoutResult(status -> {
			tables.delete("look", "look_id", id);
			tables.insert("look", "look_id", id, look);
		});
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM look WHERE id = ?").param(id).update();
	}

	@Override
	public LookRevision saveRevision(long lookId, Instant createdAt, String reason, Look look, int keep) {
		long id = transactions.execute(status -> {
			var keys = new GeneratedKeyHolder();
			jdbc.sql("INSERT INTO look_revision (look_id, created_at, reason) VALUES (?, ?, ?)")
					.params(lookId, createdAt.toString(), reason)
					.update(keys);
			var revisionId = generatedId(keys);
			tables.insert("look_revision", "revision_id", revisionId, look);
			jdbc.sql("""
					DELETE FROM look_revision WHERE look_id = ? AND id NOT IN (
					    SELECT id FROM look_revision WHERE look_id = ? ORDER BY id DESC LIMIT ?)""")
					.params(lookId, lookId, keep)
					.update();
			return revisionId;
		});
		return findRevision(id).orElseThrow();
	}

	@Override
	public List<LookRevision> findRevisions(long lookId) {
		var contents = tables.load("look_revision", "revision_id",
				"revision_id IN (SELECT id FROM look_revision WHERE look_id = ?)", lookId);
		return jdbc.sql("SELECT * FROM look_revision WHERE look_id = ? ORDER BY id DESC").param(lookId)
				.query((rs, n) -> revision(rs, contents)).list();
	}

	@Override
	public Optional<LookRevision> findRevision(long id) {
		var contents = tables.load("look_revision", "revision_id", "revision_id = ?", id);
		return jdbc.sql("SELECT * FROM look_revision WHERE id = ?").param(id)
				.query((rs, n) -> revision(rs, contents)).optional();
	}

	private static NamedLook look(ResultSet rs, Map<Long, Look> contents) throws SQLException {
		var id = rs.getLong("id");
		var ownerId = rs.getLong("owner_composition_id");
		Optional<Long> owner = rs.wasNull() ? Optional.empty() : Optional.of(ownerId);
		var spec = new LookSpec(rs.getString("name"), owner);
		return new NamedLook(id, rs.getLong("project_id"), spec, contents.getOrDefault(id, Look.EMPTY));
	}

	private static LookRevision revision(ResultSet rs, Map<Long, Look> contents) throws SQLException {
		var id = rs.getLong("id");
		return new LookRevision(id, rs.getLong("look_id"), Instant.parse(rs.getString("created_at")),
				rs.getString("reason"), contents.getOrDefault(id, Look.EMPTY));
	}
}
