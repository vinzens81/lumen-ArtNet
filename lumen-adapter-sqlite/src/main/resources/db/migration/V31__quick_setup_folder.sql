-- Compositions quick setup made before folders existed (V30 put them into "Manual") move to "Auto generated": one step,
-- played once, 0.3 s fade, 1 s hold, named "<name> – <intensity or color>".

UPDATE composition
SET folder = 'Auto generated'
WHERE folder = 'Manual'
  AND play_mode = 'once'
  AND fade_ms = 300
  AND hold_ms = 1000
  AND (SELECT count(*) FROM composition_step s WHERE s.composition_id = composition.id) = 1
  AND (name LIKE '% – 0 \%' ESCAPE '\' OR name LIKE '% – 50 \%' ESCAPE '\' OR name LIKE '% – 100 \%' ESCAPE '\'
    OR name LIKE '% – Red' OR name LIKE '% – Orange' OR name LIKE '% – Green' OR name LIKE '% – Blue'
    OR name LIKE '% – Violet');
