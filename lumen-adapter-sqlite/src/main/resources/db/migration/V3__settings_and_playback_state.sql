-- Application settings and the playback position for resuming after a restart (iteration 8)

CREATE TABLE app_setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- scene on stage per project; NULL = dark (before the first scene or after the end)
CREATE TABLE playback_state (
    project_id       INTEGER PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    current_scene_id INTEGER REFERENCES scene (id) ON DELETE SET NULL,
    updated_at       TEXT    NOT NULL
);
