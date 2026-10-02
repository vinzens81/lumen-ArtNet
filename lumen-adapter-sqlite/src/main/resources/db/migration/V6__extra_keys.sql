-- Extra keys in show mode and compositions that run only once (iteration K3)

ALTER TABLE composition ADD COLUMN once INTEGER NOT NULL DEFAULT 0 CHECK (once IN (0, 1));
-- fade in/out of an extra key (rows with key_slot)
ALTER TABLE composition ADD COLUMN key_fade_ms INTEGER CHECK (key_fade_ms IS NULL OR key_fade_ms >= 0);

-- which extra keys can be used while a scene is on stage
CREATE TABLE scene_extra_key (
    scene_id INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    key_slot INTEGER NOT NULL CHECK (key_slot BETWEEN 1 AND 8),
    PRIMARY KEY (scene_id, key_slot)
);
