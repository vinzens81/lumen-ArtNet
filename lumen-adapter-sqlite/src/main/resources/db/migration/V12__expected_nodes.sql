-- Art-Net nodes a project expects on the network (iteration H): learned when a node answers ArtPoll for one of
-- the project's universes, so a missing node is noticed – also right after a restart

CREATE TABLE expected_node (
    project_id INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    ip_address TEXT    NOT NULL,
    name       TEXT    NOT NULL,
    PRIMARY KEY (project_id, ip_address)
);
