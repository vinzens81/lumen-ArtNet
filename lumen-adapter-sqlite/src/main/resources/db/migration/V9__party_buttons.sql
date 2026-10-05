-- Party mode (iteration 11, step 1): buttons with their own values, recorded from the programmer

CREATE TABLE party_button (
    id         INTEGER PRIMARY KEY,
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    name       TEXT    NOT NULL,
    key_mode   TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash'))
);

CREATE INDEX party_button_project ON party_button (project_id, position);

-- values of a button, normalized 0..1 like scene_value
CREATE TABLE party_button_value (
    button_id  INTEGER NOT NULL REFERENCES party_button (id) ON DELETE CASCADE,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    attribute  TEXT    NOT NULL,
    value      REAL    NOT NULL CHECK (value BETWEEN 0 AND 1),
    PRIMARY KEY (button_id, fixture_id, attribute)
);
