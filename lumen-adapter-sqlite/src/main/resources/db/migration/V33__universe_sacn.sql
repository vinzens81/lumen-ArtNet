-- sACN (E1.31) output per universe: protocol 'artnet', 'sacn' or 'both'; sACN universe 1–63999, priority 0–200,
-- manual target addresses (comma separated, NULL = multicast)
ALTER TABLE universe ADD COLUMN protocol TEXT NOT NULL DEFAULT 'artnet'
    CHECK (protocol IN ('artnet', 'sacn', 'both'));
ALTER TABLE universe ADD COLUMN sacn_universe INTEGER CHECK (sacn_universe BETWEEN 1 AND 63999);
ALTER TABLE universe ADD COLUMN sacn_priority INTEGER NOT NULL DEFAULT 100 CHECK (sacn_priority BETWEEN 0 AND 200);
ALTER TABLE universe ADD COLUMN sacn_targets TEXT;

UPDATE universe SET sacn_universe = MIN(number, 63999);
