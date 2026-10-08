-- The allowed range of pan and tilt per fixture in DMX values (NULL = no limit), for the show, not the programmer.

ALTER TABLE fixture ADD COLUMN pan_min INTEGER CHECK (pan_min BETWEEN 0 AND 255);
ALTER TABLE fixture ADD COLUMN pan_max INTEGER CHECK (pan_max BETWEEN 0 AND 255);
ALTER TABLE fixture ADD COLUMN tilt_min INTEGER CHECK (tilt_min BETWEEN 0 AND 255);
ALTER TABLE fixture ADD COLUMN tilt_max INTEGER CHECK (tilt_max BETWEEN 0 AND 255);
