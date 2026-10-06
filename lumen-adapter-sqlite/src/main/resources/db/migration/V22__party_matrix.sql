-- Party mode becomes a matrix of fields that switch scenes and compositions on and off; the party buttons with
-- values of their own are gone.

DROP TABLE party_button_value;
DROP TABLE party_button;

-- size of the matrix and the lines between rows, one row per project (missing = 4 × 4, empty)
CREATE TABLE party_matrix (
    project_id INTEGER PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    rows       INTEGER NOT NULL CHECK (rows BETWEEN 1 AND 12),
    columns    INTEGER NOT NULL CHECK (columns BETWEEN 1 AND 12),
    -- rows a line is drawn above, comma-separated, e.g. '2,4'
    separators TEXT    NOT NULL DEFAULT ''
);

-- a field: plays a scene, a composition or nothing (a text); a deleted scene or composition leaves a text field
CREATE TABLE party_cell (
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    row_index      INTEGER NOT NULL CHECK (row_index >= 0),
    column_index   INTEGER NOT NULL CHECK (column_index >= 0),
    label          TEXT,
    scene_id       INTEGER REFERENCES scene (id) ON DELETE SET NULL,
    composition_id INTEGER REFERENCES composition (id) ON DELETE SET NULL,
    color          TEXT    NOT NULL,
    border_color   TEXT    NOT NULL,
    key_mode       TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash')),
    group_name     TEXT,
    PRIMARY KEY (project_id, row_index, column_index),
    CHECK (scene_id IS NULL OR composition_id IS NULL)
);

CREATE INDEX party_cell_by_scene ON party_cell (scene_id);
CREATE INDEX party_cell_by_composition ON party_cell (composition_id);
