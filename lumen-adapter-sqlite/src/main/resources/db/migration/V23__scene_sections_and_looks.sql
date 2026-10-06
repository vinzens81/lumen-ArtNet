-- Light markers in the script split a scene into parts with light of their own; scenes can also play looks directly.
-- section = '' is the start of the scene (before the first marker), otherwise the name of the marker.

CREATE TABLE scene_composition_new (
    scene_id       INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    section        TEXT    NOT NULL DEFAULT '',
    composition_id INTEGER NOT NULL REFERENCES composition (id),
    position       INTEGER NOT NULL,
    level          REAL    NOT NULL CHECK (level BETWEEN 0 AND 1),
    speed_factor   REAL    NOT NULL CHECK (speed_factor > 0),
    restart        INTEGER NOT NULL CHECK (restart IN (0, 1)),
    PRIMARY KEY (scene_id, section, composition_id)
);

INSERT INTO scene_composition_new (scene_id, section, composition_id, position, level, speed_factor, restart)
SELECT scene_id, '', composition_id, position, level, speed_factor, restart FROM scene_composition;

DROP TABLE scene_composition;
ALTER TABLE scene_composition_new RENAME TO scene_composition;

CREATE INDEX scene_composition_by_composition ON scene_composition (composition_id);

CREATE TABLE scene_look (
    scene_id INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    section  TEXT    NOT NULL DEFAULT '',
    look_id  INTEGER NOT NULL REFERENCES look (id),
    position INTEGER NOT NULL,
    level    REAL    NOT NULL CHECK (level BETWEEN 0 AND 1),
    PRIMARY KEY (scene_id, section, look_id)
);

CREATE INDEX scene_look_by_look ON scene_look (look_id);
