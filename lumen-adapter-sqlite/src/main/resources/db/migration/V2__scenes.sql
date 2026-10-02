-- Scenes (cue list) and their values (iteration 6)

CREATE TABLE scene (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    number         TEXT    NOT NULL, -- normalized decimal, e.g. '2.5'
    name           TEXT    NOT NULL,
    cue_note       TEXT,
    auto_follow_ms INTEGER CHECK (auto_follow_ms IS NULL OR auto_follow_ms > 0),
    UNIQUE (project_id, number)
);

-- normalized values (0.0–1.0) per fixture and attribute ('dimmer', 'red', 'generic:Program speed', …)
CREATE TABLE scene_value (
    scene_id   INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute  TEXT    NOT NULL,
    value      REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (scene_id, fixture_id, attribute)
);

CREATE INDEX scene_value_fixture ON scene_value (fixture_id);
