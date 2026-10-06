-- How a fixture is drawn in the stage view: auto, spot, bar or strip, turned and (bar/strip) stretched

ALTER TABLE fixture ADD COLUMN stage_shape TEXT NOT NULL DEFAULT 'auto';
ALTER TABLE fixture ADD COLUMN stage_rotation INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fixture ADD COLUMN stage_length REAL NOT NULL DEFAULT 0.15;
