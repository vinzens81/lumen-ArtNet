-- Moves: figures (shapes in the pan/tilt grid) laid onto pan/tilt fixtures. Coordinates are normalized:
-- 0.0–1.0 = DMX 0–255, finer than one DMX step.

CREATE TABLE figure (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    closed     INTEGER NOT NULL CHECK (closed IN (0, 1))
);

CREATE INDEX figure_by_project ON figure (project_id);

CREATE TABLE figure_point (
    figure_id INTEGER NOT NULL REFERENCES figure (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    x         REAL    NOT NULL CHECK (x BETWEEN 0 AND 1),
    y         REAL    NOT NULL CHECK (y BETWEEN 0 AND 1),
    PRIMARY KEY (figure_id, position)
);

-- a figure used by a move cannot be deleted
CREATE TABLE move (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id   INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name         TEXT    NOT NULL,
    figure_id    INTEGER NOT NULL REFERENCES figure (id) ON DELETE RESTRICT,
    size_x       REAL    NOT NULL,
    size_y       REAL    NOT NULL,
    shift_x      REAL    NOT NULL,
    shift_y      REAL    NOT NULL,
    rotation_deg REAL    NOT NULL,
    duration_ms  INTEGER NOT NULL CHECK (duration_ms > 0),
    repeat_mode  TEXT    NOT NULL CHECK (repeat_mode IN ('loop', 'once', 'ping_pong')),
    step_offset  INTEGER NOT NULL CHECK (step_offset >= 0),
    once_start   TEXT    NOT NULL CHECK (once_start IN ('sequential', 'together'))
);

CREATE INDEX move_by_project ON move (project_id);
CREATE INDEX move_by_figure ON move (figure_id);

-- a fixture deleted from the patch drops out of its moves
CREATE TABLE move_fixture (
    move_id    INTEGER NOT NULL REFERENCES move (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    fixture_id INTEGER NOT NULL REFERENCES fixture (id) ON DELETE CASCADE,
    shift_x    REAL    NOT NULL,
    shift_y    REAL    NOT NULL,
    mirror_x   INTEGER NOT NULL CHECK (mirror_x IN (0, 1)),
    mirror_y   INTEGER NOT NULL CHECK (mirror_y IN (0, 1)),
    reverse    INTEGER NOT NULL CHECK (reverse IN (0, 1)),
    PRIMARY KEY (move_id, fixture_id)
);

CREATE INDEX move_fixture_by_fixture ON move_fixture (fixture_id);
