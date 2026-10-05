-- Network interface a universe is sent on (iteration N), e.g. 'en7'; NULL = all networks
ALTER TABLE universe ADD COLUMN network_interface TEXT;
