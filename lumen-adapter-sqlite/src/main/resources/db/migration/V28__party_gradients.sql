-- Party fields can cover several columns and show a gradient that sets the intensity or the color of fixtures.

ALTER TABLE party_cell ADD COLUMN span INTEGER NOT NULL DEFAULT 1 CHECK (span BETWEEN 1 AND 12);
-- 'dimmer' or 'color'; NULL = no gradient. A gradient whose fixtures are all deleted leaves a text field.
ALTER TABLE party_cell ADD COLUMN fader_kind TEXT CHECK (fader_kind IN ('dimmer', 'color'));

-- the fixtures a gradient sets, in order
CREATE TABLE party_cell_fixture (
    project_id   INTEGER NOT NULL,
    row_index    INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    position     INTEGER NOT NULL,
    fixture_id   INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, row_index, column_index, position),
    FOREIGN KEY (project_id, row_index, column_index)
        REFERENCES party_cell (project_id, row_index, column_index) ON DELETE CASCADE
);

CREATE INDEX party_cell_fixture_by_fixture ON party_cell_fixture (fixture_id);
