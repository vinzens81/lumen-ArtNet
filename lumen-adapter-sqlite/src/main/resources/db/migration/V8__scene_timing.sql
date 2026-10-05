-- Fade times per scene (iteration 10): NULL = the project's fade time
ALTER TABLE scene ADD COLUMN fade_in_ms INTEGER CHECK (fade_in_ms >= 0);
ALTER TABLE scene ADD COLUMN fade_out_ms INTEGER CHECK (fade_out_ms >= 0);
-- the fade-in starts this long after GO
ALTER TABLE scene ADD COLUMN delay_ms INTEGER NOT NULL DEFAULT 0 CHECK (delay_ms >= 0);
