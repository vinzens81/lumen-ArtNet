package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.project.port.out.UniverseRepository;
import de.vinzens.lumen.domain.project.ArtNetAddress;
import de.vinzens.lumen.domain.project.ArtNetTarget;
import de.vinzens.lumen.domain.project.Universe;
import de.vinzens.lumen.domain.project.UniverseSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.List;
import java.util.Optional;

import static de.vinzens.lumen.adapter.sqlite.GeneratedKeys.generatedId;

public class SqliteUniverseRepository implements UniverseRepository {

	private static final String SELECT = "SELECT * FROM universe";

	private static final RowMapper<Universe> MAPPER = (rs, row) -> new Universe(
			rs.getLong("id"),
			rs.getLong("project_id"),
			new UniverseSpec(
					rs.getInt("number"),
					Optional.ofNullable(rs.getString("name")),
					new ArtNetAddress(rs.getInt("artnet_net"), rs.getInt("artnet_subnet"), rs.getInt("artnet_universe")),
					ArtNetTarget.parse(rs.getString("target")),
					Optional.ofNullable(rs.getString("network_interface"))));

	private final JdbcClient jdbc;

	public SqliteUniverseRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Universe> findByProject(long projectId) {
		return jdbc.sql(SELECT + " WHERE project_id = ? ORDER BY number").param(projectId).query(MAPPER).list();
	}

	@Override
	public Optional<Universe> findById(long id) {
		return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(MAPPER).optional();
	}

	@Override
	public Universe create(long projectId, UniverseSpec spec) {
		var keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO universe (project_id, number, name, artnet_net, artnet_subnet, artnet_universe, target,
				                      network_interface)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(projectId, spec.number(), spec.name().orElse(null), spec.artNet().net(),
						spec.artNet().subnet(), spec.artNet().universe(), spec.target().asText(),
						spec.networkInterface().orElse(null))
				.update(keys);
		return findById(generatedId(keys)).orElseThrow();
	}

	@Override
	public Universe update(long id, UniverseSpec spec) {
		jdbc.sql("""
				UPDATE universe SET number = ?, name = ?, artnet_net = ?, artnet_subnet = ?, artnet_universe = ?,
				    target = ?, network_interface = ?
				WHERE id = ?""")
				.params(spec.number(), spec.name().orElse(null), spec.artNet().net(), spec.artNet().subnet(),
						spec.artNet().universe(), spec.target().asText(), spec.networkInterface().orElse(null), id)
				.update();
		return findById(id).orElseThrow();
	}

	@Override
	public void delete(long id) {
		jdbc.sql("DELETE FROM universe WHERE id = ?").param(id).update();
	}
}
