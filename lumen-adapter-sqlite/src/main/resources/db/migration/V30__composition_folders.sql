-- Compositions are sorted into folders, e.g. "Manual" and "Auto generated" (made by quick setup).

ALTER TABLE composition ADD COLUMN folder TEXT NOT NULL DEFAULT 'Manual';
