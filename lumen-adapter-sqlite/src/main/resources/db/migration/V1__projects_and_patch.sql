-- Projects, universes, patch and groups (iteration 2)

CREATE TABLE project (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    venue       TEXT,
    fade_in_ms  INTEGER NOT NULL DEFAULT 3000,
    fade_out_ms INTEGER NOT NULL DEFAULT 3000,
    active      INTEGER NOT NULL DEFAULT 0 CHECK (active IN (0, 1)),
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

-- at most one active project
CREATE UNIQUE INDEX project_single_active ON project (active) WHERE active = 1;

CREATE TABLE universe (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    number          INTEGER NOT NULL CHECK (number >= 1),
    name            TEXT,
    artnet_net      INTEGER NOT NULL DEFAULT 0 CHECK (artnet_net BETWEEN 0 AND 127),
    artnet_subnet   INTEGER NOT NULL DEFAULT 0 CHECK (artnet_subnet BETWEEN 0 AND 15),
    artnet_universe INTEGER NOT NULL DEFAULT 0 CHECK (artnet_universe BETWEEN 0 AND 15),
    target          TEXT    NOT NULL,
    UNIQUE (project_id, number)
);

CREATE TABLE fixture (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    short_name      TEXT,
    fixture_type_id TEXT    NOT NULL,
    mode_name       TEXT    NOT NULL,
    universe_id     INTEGER NOT NULL REFERENCES universe (id) ON DELETE RESTRICT,
    address         INTEGER NOT NULL CHECK (address BETWEEN 1 AND 512),
    pos_x           REAL,
    pos_y           REAL,
    sort_order      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX fixture_project ON fixture (project_id);
CREATE INDEX fixture_universe ON fixture (universe_id);

CREATE TABLE fixture_group (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    color      TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE fixture_group_member (
    group_id   INTEGER NOT NULL REFERENCES fixture_group (id) ON DELETE CASCADE,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    PRIMARY KEY (group_id, fixture_id)
);
