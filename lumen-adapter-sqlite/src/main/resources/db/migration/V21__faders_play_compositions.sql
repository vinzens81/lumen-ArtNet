-- Faders play a composition instead of a scene, and scenes outside the cue list are gone: GO plays every scene.
-- A fader takes the first composition of its scene (and the scene's name, unless it had its own).

-- projects whose show from before iteration S still waits for its conversion (as SqliteLegacyShowStore finds them):
-- their scenes have no compositions yet, so the converter gets what it needs to do the same later
CREATE TEMP TABLE pending_project AS
SELECT id FROM project p
WHERE NOT EXISTS (SELECT 1 FROM legacy_conversion c WHERE c.project_id = p.id)
  AND (EXISTS (SELECT 1 FROM legacy_scene_value v JOIN scene s ON s.id = v.scene_id WHERE s.project_id = p.id)
    OR EXISTS (SELECT 1 FROM legacy_composition c WHERE c.project_id = p.id)
    OR EXISTS (SELECT 1 FROM legacy_extra_key_scene k WHERE k.project_id = p.id));

CREATE TABLE legacy_outside_cue_list (
    scene_id INTEGER PRIMARY KEY REFERENCES scene (id) ON DELETE CASCADE
);

INSERT INTO legacy_outside_cue_list (scene_id)
SELECT id FROM scene WHERE outside_cue_list = 1 AND project_id IN (SELECT id FROM pending_project);

CREATE TABLE legacy_fader (
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    slot       INTEGER NOT NULL,
    name       TEXT,
    scene_id   INTEGER NOT NULL REFERENCES scene (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, slot)
);

INSERT INTO legacy_fader (project_id, slot, name, scene_id)
SELECT project_id, slot, name, scene_id FROM fader WHERE project_id IN (SELECT id FROM pending_project);

CREATE TABLE fader_new (
    project_id     INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    slot           INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 8),
    name           TEXT,
    composition_id INTEGER NOT NULL REFERENCES composition (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, slot)
);

INSERT INTO fader_new (project_id, slot, name, composition_id)
SELECT f.project_id, f.slot, COALESCE(f.name, s.name), sc.composition_id
FROM fader f
JOIN scene s ON s.id = f.scene_id
JOIN scene_composition sc ON sc.scene_id = f.scene_id
WHERE f.project_id NOT IN (SELECT id FROM pending_project)
  AND sc.position = (SELECT MIN(position) FROM scene_composition WHERE scene_id = f.scene_id);

DROP TABLE fader;
ALTER TABLE fader_new RENAME TO fader;

CREATE INDEX fader_by_composition ON fader (composition_id);

DELETE FROM scene WHERE outside_cue_list = 1 AND project_id NOT IN (SELECT id FROM pending_project);
ALTER TABLE scene DROP COLUMN outside_cue_list;

DROP TABLE pending_project;
