-- Whole scenes on extra keys (iteration K3)

-- 1 = only played by extra keys, GO skips the scene
ALTER TABLE scene ADD COLUMN key_only INTEGER NOT NULL DEFAULT 0 CHECK (key_only IN (0, 1));

-- extra keys that play a scene (keys with a composition are rows in composition with key_slot)
CREATE TABLE extra_key_scene (
    project_id  INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    key_slot    INTEGER NOT NULL CHECK (key_slot BETWEEN 1 AND 8),
    scene_id    INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    key_mode    TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash')),
    key_fade_ms INTEGER NOT NULL CHECK (key_fade_ms >= 0),
    PRIMARY KEY (project_id, key_slot)
);
