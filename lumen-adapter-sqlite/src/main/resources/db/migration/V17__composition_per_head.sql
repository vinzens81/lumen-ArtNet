-- Compositions can run along the heads of multi-pixel fixtures (each head an element of its own)

ALTER TABLE composition ADD COLUMN per_head INTEGER NOT NULL DEFAULT 0;
