-- Fade curves: one per project, optionally overridden per scene (NULL = the project's)

ALTER TABLE project ADD COLUMN fade_curve TEXT NOT NULL DEFAULT 'linear';
ALTER TABLE scene ADD COLUMN fade_curve TEXT;
