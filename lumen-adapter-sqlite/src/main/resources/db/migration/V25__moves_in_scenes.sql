-- Scenes, light markers, keys and party fields play moves. Pan and tilt come only from moves now: looks and presets
-- no longer hold positions.

-- section = '' is the start of the scene, otherwise the name of the light marker (as in scene_composition)
CREATE TABLE scene_move (
    scene_id INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    section  TEXT    NOT NULL DEFAULT '',
    move_id  INTEGER NOT NULL REFERENCES move (id),
    position INTEGER NOT NULL,
    PRIMARY KEY (scene_id, section, move_id)
);

CREATE INDEX scene_move_by_move ON scene_move (move_id);

-- a key plays a composition or a move
CREATE TABLE scene_key_new (
    scene_id       INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    slot           INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 8),
    composition_id INTEGER REFERENCES composition (id),
    move_id        INTEGER REFERENCES move (id),
    key_mode       TEXT    NOT NULL CHECK (key_mode IN ('toggle', 'flash')),
    fade_ms        INTEGER NOT NULL CHECK (fade_ms >= 0),
    PRIMARY KEY (scene_id, slot),
    CHECK ((composition_id IS NULL) <> (move_id IS NULL))
);

INSERT INTO scene_key_new (scene_id, slot, composition_id, key_mode, fade_ms)
SELECT scene_id, slot, composition_id, key_mode, fade_ms FROM scene_key;

DROP TABLE scene_key;
ALTER TABLE scene_key_new RENAME TO scene_key;

CREATE INDEX scene_key_by_composition ON scene_key (composition_id);
CREATE INDEX scene_key_by_move ON scene_key (move_id);

-- a deleted move leaves a text field (deleting a played move is refused anyway)
ALTER TABLE party_cell ADD COLUMN move_id INTEGER REFERENCES move (id) ON DELETE SET NULL;

CREATE INDEX party_cell_by_move ON party_cell (move_id);

-- positions are moves now
DELETE FROM look_value WHERE attribute IN ('pan', 'tilt') OR attribute LIKE 'pan@%' OR attribute LIKE 'tilt@%';
DELETE FROM look_revision_value
WHERE attribute IN ('pan', 'tilt') OR attribute LIKE 'pan@%' OR attribute LIKE 'tilt@%';
DELETE FROM look_preset WHERE attribute_group = 'position';
DELETE FROM look_revision_preset WHERE attribute_group = 'position';
DELETE FROM preset WHERE attribute_group = 'position';
