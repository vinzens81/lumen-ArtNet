package de.vinzens.lumen.adapter.sqlite;

import de.vinzens.lumen.application.playback.port.out.PlaybackStateRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.Optional;

public class SqlitePlaybackStateRepository implements PlaybackStateRepository {

	private final JdbcClient jdbc;

	public SqlitePlaybackStateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Long> currentScene(long projectId) {
		return jdbc.sql("SELECT current_scene_id FROM playback_state WHERE project_id = ?")
				.param(projectId)
				.query((rs, row) -> {
					var sceneId = rs.getLong("current_scene_id");
					return rs.wasNull() ? Optional.<Long>empty() : Optional.of(sceneId);
				})
				.optional()
				.flatMap(sceneId -> sceneId);
	}

	@Override
	public void save(long projectId, Optional<Long> sceneId, Instant now) {
		jdbc.sql("""
				INSERT INTO playback_state (project_id, current_scene_id, updated_at) VALUES (?, ?, ?)
				ON CONFLICT (project_id) DO UPDATE
				SET current_scene_id = excluded.current_scene_id, updated_at = excluded.updated_at""")
				.params(projectId, sceneId.orElse(null), now.toString())
				.update();
	}
}
