-- Floor plan image behind the stage view (iteration 9)

CREATE TABLE stage_plan (
    project_id   INTEGER PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    content_type TEXT    NOT NULL,
    data         BLOB    NOT NULL
);
