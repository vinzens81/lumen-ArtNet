-- Earlier looks of a scene, saved before its values were replaced (undo / history)

CREATE TABLE scene_revision (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    scene_id   INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    created_at TEXT    NOT NULL,
    reason     TEXT    NOT NULL
);

CREATE INDEX scene_revision_scene ON scene_revision (scene_id);

CREATE TABLE scene_revision_value (
    revision_id INTEGER NOT NULL REFERENCES scene_revision (id) ON DELETE CASCADE,
    fixture_id  INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute   TEXT    NOT NULL,
    value       REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (revision_id, fixture_id, attribute)
);
