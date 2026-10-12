-- Shared looks are sorted into folders like compositions. Looks quick setup made (each the only step of a composition
-- of the same name in "Auto generated") go to "Auto generated", all others to "Manual".

ALTER TABLE look ADD COLUMN folder TEXT NOT NULL DEFAULT 'Manual';

UPDATE look
SET folder = 'Auto generated'
WHERE owner_composition_id IS NULL
  AND EXISTS (SELECT 1
              FROM composition c
                   JOIN composition_step s ON s.composition_id = c.id
              WHERE s.look_id = look.id
                AND c.name = look.name
                AND c.folder = 'Auto generated'
                AND (SELECT count(*) FROM composition_step o WHERE o.composition_id = c.id) = 1);
