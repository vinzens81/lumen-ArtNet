-- Fade times of the change into a part of a scene (after a light marker): NULL = the fade times of the scene
CREATE TABLE scene_section (
    scene_id    INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    section     TEXT    NOT NULL CHECK (section <> ''),
    fade_in_ms  INTEGER CHECK (fade_in_ms >= 0),
    fade_out_ms INTEGER CHECK (fade_out_ms >= 0),
    PRIMARY KEY (scene_id, section)
);
