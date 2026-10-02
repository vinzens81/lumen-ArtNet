-- Compositions: library, copies in scenes and (later) extra keys in one table (iteration K)

CREATE TABLE composition (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id   INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    scene_id     INTEGER REFERENCES scene (id) ON DELETE CASCADE,       -- set = copy in this scene
    key_slot     INTEGER,                                               -- set = extra key in show mode (K3)
    source_id    INTEGER REFERENCES composition (id) ON DELETE SET NULL, -- library origin of a copy
    name         TEXT    NOT NULL,
    kind         TEXT    NOT NULL CHECK (kind IN ('chase', 'color_cycle', 'rainbow', 'wave', 'flicker', 'steps')),
    params       TEXT    NOT NULL, -- JSON, schema per kind
    target       TEXT    NOT NULL CHECK (target IN ('group', 'fixtures', 'steps')),
    group_id     INTEGER REFERENCES fixture_group (id) ON DELETE SET NULL, -- target 'group'; NULL = group deleted
    position     INTEGER NOT NULL DEFAULT 0,
    level        REAL    NOT NULL DEFAULT 1.0 CHECK (level BETWEEN 0 AND 1),
    speed_factor REAL    NOT NULL DEFAULT 1.0 CHECK (speed_factor > 0),
    restart      INTEGER NOT NULL DEFAULT 0 CHECK (restart IN (0, 1)),
    key_mode     TEXT CHECK (key_mode IN ('toggle', 'flash')),
    CHECK (scene_id IS NULL OR key_slot IS NULL),
    UNIQUE (project_id, key_slot)
);

CREATE INDEX composition_project ON composition (project_id);
CREATE INDEX composition_scene ON composition (scene_id);

-- fixed fixture list as target (target 'fixtures')
CREATE TABLE composition_target (
    composition_id INTEGER NOT NULL REFERENCES composition (id) ON DELETE CASCADE,
    fixture_id     INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    PRIMARY KEY (composition_id, fixture_id)
);

CREATE TABLE composition_step (
    composition_id INTEGER NOT NULL REFERENCES composition (id) ON DELETE CASCADE,
    step_index     INTEGER NOT NULL,
    hold_ms        INTEGER NOT NULL CHECK (hold_ms >= 0),
    fade_ms        INTEGER NOT NULL CHECK (fade_ms >= 0),
    PRIMARY KEY (composition_id, step_index)
);

-- values per step, normalized 0..1 like scene_value
CREATE TABLE composition_step_value (
    composition_id INTEGER NOT NULL,
    step_index     INTEGER NOT NULL,
    fixture_id     INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute      TEXT    NOT NULL,
    value          REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (composition_id, step_index, fixture_id, attribute),
    FOREIGN KEY (composition_id, step_index) REFERENCES composition_step (composition_id, step_index) ON DELETE CASCADE
);

CREATE INDEX composition_target_fixture ON composition_target (fixture_id);
CREATE INDEX composition_step_value_fixture ON composition_step_value (fixture_id);
