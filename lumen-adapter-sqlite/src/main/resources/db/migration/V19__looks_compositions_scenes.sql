-- Iteration S: looks → compositions → scenes.
-- The tables of the old model are kept as legacy_*; LegacyMigrationService converts their content at startup.

ALTER TABLE scene_value RENAME TO legacy_scene_value;
ALTER TABLE scene_preset RENAME TO legacy_scene_preset;
ALTER TABLE scene_revision RENAME TO legacy_scene_revision;
ALTER TABLE scene_revision_value RENAME TO legacy_scene_revision_value;
ALTER TABLE scene_revision_preset RENAME TO legacy_scene_revision_preset;
ALTER TABLE composition_step_value RENAME TO legacy_composition_step_value;
ALTER TABLE composition_step RENAME TO legacy_composition_step;
ALTER TABLE composition_target RENAME TO legacy_composition_target;
ALTER TABLE composition RENAME TO legacy_composition;
ALTER TABLE scene_extra_key RENAME TO legacy_scene_extra_key;
ALTER TABLE extra_key_scene RENAME TO legacy_extra_key_scene;

-- scenes only for extra keys stay as scenes outside the cue list (played by faders)
ALTER TABLE scene RENAME COLUMN key_only TO outside_cue_list;

-- projects whose legacy data has been converted
CREATE TABLE legacy_conversion (
    project_id   INTEGER PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    converted_at TEXT    NOT NULL
);

CREATE TABLE composition (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name           TEXT    NOT NULL,
    fade_ms        INTEGER NOT NULL CHECK (fade_ms >= 0),
    hold_ms        INTEGER NOT NULL CHECK (hold_ms >= 0),
    play_mode      TEXT    NOT NULL CHECK (play_mode IN ('loop', 'ping_pong', 'once')),
    intensity_mode TEXT    NOT NULL CHECK (intensity_mode IN ('absolute', 'relative')),
    -- what the steps were generated from, only for generating them again: effect kind, its parameters (JSON),
    -- target group or fixtures, per head
    generator_kind TEXT CHECK (generator_kind IN ('chase', 'color_cycle', 'rainbow', 'wave', 'flicker')),
    generator      TEXT,
    generator_group_id INTEGER REFERENCES fixture_group (id) ON DELETE SET NULL,
    generator_per_head INTEGER NOT NULL DEFAULT 0 CHECK (generator_per_head IN (0, 1))
);

CREATE INDEX composition_by_project ON composition (project_id);

-- fixed fixture list as generator target
CREATE TABLE composition_generator_fixture (
    composition_id INTEGER NOT NULL REFERENCES composition (id) ON DELETE CASCADE,
    fixture_id     INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    PRIMARY KEY (composition_id, fixture_id)
);

CREATE TABLE look (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id           INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name                 TEXT    NOT NULL,
    -- set = the look belongs to this composition (recorded step or generated); NULL = shared
    owner_composition_id INTEGER REFERENCES composition (id) ON DELETE CASCADE
);

CREATE INDEX look_by_project ON look (project_id);
CREATE INDEX look_by_owner ON look (owner_composition_id);

-- normalized values (0.0–1.0) per fixture and attribute
CREATE TABLE look_value (
    look_id    INTEGER NOT NULL REFERENCES look (id) ON DELETE CASCADE,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute  TEXT    NOT NULL,
    value      REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (look_id, fixture_id, attribute)
);

CREATE INDEX look_value_by_fixture ON look_value (fixture_id);

-- which preset the values of a fixture come from, per attribute group
CREATE TABLE look_preset (
    look_id         INTEGER NOT NULL REFERENCES look (id) ON DELETE CASCADE,
    fixture_id      INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute_group TEXT    NOT NULL,
    preset_id       INTEGER NOT NULL REFERENCES preset (id) ON DELETE CASCADE,
    PRIMARY KEY (look_id, fixture_id, attribute_group)
);

CREATE INDEX look_preset_by_preset ON look_preset (preset_id);

CREATE TABLE look_revision (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    look_id    INTEGER NOT NULL REFERENCES look (id) ON DELETE CASCADE,
    created_at TEXT    NOT NULL,
    reason     TEXT    NOT NULL
);

CREATE INDEX look_revision_by_look ON look_revision (look_id);

CREATE TABLE look_revision_value (
    revision_id INTEGER NOT NULL REFERENCES look_revision (id) ON DELETE CASCADE,
    fixture_id  INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute   TEXT    NOT NULL,
    value       REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (revision_id, fixture_id, attribute)
);

CREATE TABLE look_revision_preset (
    revision_id     INTEGER NOT NULL REFERENCES look_revision (id) ON DELETE CASCADE,
    fixture_id      INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute_group TEXT    NOT NULL,
    preset_id       INTEGER NOT NULL REFERENCES preset (id) ON DELETE CASCADE,
    PRIMARY KEY (revision_id, fixture_id, attribute_group)
);

-- a step plays a look; a look still played by a step cannot be deleted
CREATE TABLE composition_step (
    composition_id INTEGER NOT NULL REFERENCES composition (id) ON DELETE CASCADE,
    step_index     INTEGER NOT NULL,
    look_id        INTEGER NOT NULL REFERENCES look (id),
    fade_ms        INTEGER CHECK (fade_ms >= 0), -- NULL = the composition's default
    hold_ms        INTEGER CHECK (hold_ms >= 0),
    PRIMARY KEY (composition_id, step_index)
);

CREATE INDEX composition_step_by_look ON composition_step (look_id);

CREATE TABLE scene_composition (
    scene_id       INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    composition_id INTEGER NOT NULL REFERENCES composition (id),
    position       INTEGER NOT NULL,
    level          REAL    NOT NULL CHECK (level BETWEEN 0 AND 1),
    speed_factor   REAL    NOT NULL CHECK (speed_factor > 0),
    restart        INTEGER NOT NULL CHECK (restart IN (0, 1)),
    PRIMARY KEY (scene_id, composition_id)
);

CREATE INDEX scene_composition_by_composition ON scene_composition (composition_id);

CREATE TABLE scene_key (
    scene_id       INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    slot           INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 8),
    composition_id INTEGER NOT NULL REFERENCES composition (id),
    key_mode       TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash')),
    fade_ms        INTEGER NOT NULL CHECK (fade_ms >= 0),
    PRIMARY KEY (scene_id, slot)
);

CREATE INDEX scene_key_by_composition ON scene_key (composition_id);
