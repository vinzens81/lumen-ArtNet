-- Text spoken on stage during the scene (iteration T); **…** marks stage directions
ALTER TABLE scene ADD COLUMN script TEXT NOT NULL DEFAULT '';
