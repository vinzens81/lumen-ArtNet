-- A step can start together with the step before it instead of after it (parallel looks in one row).
ALTER TABLE composition_step ADD COLUMN parallel INTEGER NOT NULL DEFAULT 0;
