-- Move in dark: heads that point somewhere else at the GO into a scene dim out, move while dark and dim in.
-- NULL times of a scene = the project's.

ALTER TABLE scene ADD COLUMN move_in_dark INTEGER NOT NULL DEFAULT 0 CHECK (move_in_dark IN (0, 1));
ALTER TABLE scene ADD COLUMN dim_out_ms INTEGER CHECK (dim_out_ms >= 0);
ALTER TABLE scene ADD COLUMN travel_ms INTEGER CHECK (travel_ms >= 0);

ALTER TABLE project ADD COLUMN dim_out_ms INTEGER NOT NULL DEFAULT 500 CHECK (dim_out_ms >= 0);
ALTER TABLE project ADD COLUMN travel_ms INTEGER NOT NULL DEFAULT 1500 CHECK (travel_ms >= 0);
