package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.playback.port.out.FaderRepository;
import de.vinzens.lumen.domain.composition.Fader;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class SqliteFaderRepository implements FaderRepository {

	private final JdbcClient jdbc;

	public SqliteFaderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Fader> findByProject(long projectId) {
		return jdbc.sql("SELECT * FROM fader WHERE project_id = ? ORDER BY slot").param(projectId)
				.query((rs, rowNum) -> new Fader(rs.getInt("slot"), Optional.ofNullable(rs.getString("name")),
						rs.getLong("composition_id")))
				.list();
	}

	@Override
	public Fader save(long projectId, Fader fader) {
		jdbc.sql("""
				INSERT INTO fader (project_id, slot, name, composition_id) VALUES (?, ?, ?, ?)
				ON CONFLICT (project_id, slot) DO UPDATE SET name = excluded.name,
				    composition_id = excluded.composition_id""")
				.params(projectId, fader.slot(), fader.name().orElse(null), fader.compositionId())
				.update();
		return fader;
	}

	@Override
	public void delete(long projectId, int slot) {
		jdbc.sql("DELETE FROM fader WHERE project_id = ? AND slot = ?").params(projectId, slot).update();
	}
}
