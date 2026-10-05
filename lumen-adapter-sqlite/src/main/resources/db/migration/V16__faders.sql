-- Faders of the show (e.g. house lights): each plays a scene at the level it is pushed to

CREATE TABLE fader (
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    slot       INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 8),
    name       TEXT,
    scene_id   INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, slot)
);
