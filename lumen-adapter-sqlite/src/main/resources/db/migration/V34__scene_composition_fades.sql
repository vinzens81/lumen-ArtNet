-- Fade times of a composition in a scene: NULL = the fade times of the scene
ALTER TABLE scene_composition ADD COLUMN fade_in_ms INTEGER CHECK (fade_in_ms >= 0);
ALTER TABLE scene_composition ADD COLUMN fade_out_ms INTEGER CHECK (fade_out_ms >= 0);
