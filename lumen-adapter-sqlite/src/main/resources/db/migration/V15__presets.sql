-- Presets: named colors, positions or beam settings per fixture, referred to by scenes

CREATE TABLE preset (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    attribute_group TEXT    NOT NULL CHECK (attribute_group IN ('color', 'position', 'beam'))
);

CREATE TABLE preset_value (
    preset_id  INTEGER NOT NULL REFERENCES preset (id) ON DELETE CASCADE,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute  TEXT    NOT NULL,
    value      REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (preset_id, fixture_id, attribute)
);

-- which preset the values of a fixture in a scene come from, per attribute group
CREATE TABLE scene_preset (
    scene_id        INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    fixture_id      INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute_group TEXT    NOT NULL,
    preset_id       INTEGER NOT NULL REFERENCES preset (id) ON DELETE CASCADE,
    PRIMARY KEY (scene_id, fixture_id, attribute_group)
);

CREATE INDEX scene_preset_preset ON scene_preset (preset_id);

CREATE TABLE scene_revision_preset (
    revision_id     INTEGER NOT NULL REFERENCES scene_revision (id) ON DELETE CASCADE,
    fixture_id      INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute_group TEXT    NOT NULL,
    preset_id       INTEGER NOT NULL REFERENCES preset (id) ON DELETE CASCADE,
    PRIMARY KEY (revision_id, fixture_id, attribute_group)
);
