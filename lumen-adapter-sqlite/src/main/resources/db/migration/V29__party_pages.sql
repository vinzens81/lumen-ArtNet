-- The party matrix gets pages (tabs), fields can cover several rows, and a field can be a pan/tilt pad.

-- a page of the party matrix: its name, size and the lines between rows; the matrix of a project becomes page 0
CREATE TABLE party_page (
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    page_index INTEGER NOT NULL CHECK (page_index BETWEEN 0 AND 11),
    name       TEXT    NOT NULL,
    rows       INTEGER NOT NULL CHECK (rows BETWEEN 1 AND 12),
    columns    INTEGER NOT NULL CHECK (columns BETWEEN 1 AND 12),
    -- rows a line is drawn above, comma-separated, e.g. '2,4'
    separators TEXT    NOT NULL DEFAULT '',
    PRIMARY KEY (project_id, page_index)
);

INSERT INTO party_page (project_id, page_index, name, rows, columns, separators)
SELECT project_id, 0, 'Party', rows, columns, separators FROM party_matrix;

CREATE TABLE party_cell_new (
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    page_index     INTEGER NOT NULL,
    row_index      INTEGER NOT NULL CHECK (row_index >= 0),
    column_index   INTEGER NOT NULL CHECK (column_index >= 0),
    label          TEXT,
    scene_id       INTEGER REFERENCES scene (id) ON DELETE SET NULL,
    composition_id INTEGER REFERENCES composition (id) ON DELETE SET NULL,
    move_id        INTEGER REFERENCES move (id) ON DELETE SET NULL,
    color          TEXT    NOT NULL,
    border_color   TEXT    NOT NULL,
    key_mode       TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash')),
    group_name     TEXT,
    span           INTEGER NOT NULL DEFAULT 1 CHECK (span BETWEEN 1 AND 12),
    row_span       INTEGER NOT NULL DEFAULT 1 CHECK (row_span BETWEEN 1 AND 12),
    -- NULL = no gradient or pad. One whose fixtures are all deleted leaves a text field.
    fader_kind     TEXT CHECK (fader_kind IN ('dimmer', 'color', 'pan-tilt')),
    PRIMARY KEY (project_id, page_index, row_index, column_index),
    FOREIGN KEY (project_id, page_index) REFERENCES party_page (project_id, page_index) ON DELETE CASCADE,
    CHECK (scene_id IS NULL OR composition_id IS NULL)
);

INSERT INTO party_cell_new (project_id, page_index, row_index, column_index, label, scene_id, composition_id, move_id,
                            color, border_color, key_mode, group_name, span, fader_kind)
SELECT project_id, 0, row_index, column_index, label, scene_id, composition_id, move_id, color, border_color, key_mode,
       group_name, span, fader_kind
FROM party_cell
WHERE project_id IN (SELECT project_id FROM party_page);

-- the fixtures a gradient or pad sets, in order
CREATE TABLE party_cell_fixture_new (
    project_id   INTEGER NOT NULL,
    page_index   INTEGER NOT NULL,
    row_index    INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    position     INTEGER NOT NULL,
    fixture_id   INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, page_index, row_index, column_index, position),
    FOREIGN KEY (project_id, page_index, row_index, column_index)
        REFERENCES party_cell_new (project_id, page_index, row_index, column_index) ON DELETE CASCADE
);

INSERT INTO party_cell_fixture_new (project_id, page_index, row_index, column_index, position, fixture_id)
SELECT f.project_id, 0, f.row_index, f.column_index, f.position, f.fixture_id
FROM party_cell_fixture f
WHERE f.project_id IN (SELECT project_id FROM party_page);

DROP TABLE party_cell_fixture;
DROP TABLE party_cell;
DROP TABLE party_matrix;
ALTER TABLE party_cell_new RENAME TO party_cell;
ALTER TABLE party_cell_fixture_new RENAME TO party_cell_fixture;

CREATE INDEX party_cell_by_scene ON party_cell (scene_id);
CREATE INDEX party_cell_by_composition ON party_cell (composition_id);
CREATE INDEX party_cell_by_move ON party_cell (move_id);
CREATE INDEX party_cell_fixture_by_fixture ON party_cell_fixture (fixture_id);
