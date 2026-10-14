# Project data (SQLite)

A **project** is a specific theatre play or a specific venue. It records which fixtures from the library
([FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md)) are **actually rigged**, how they are patched and which scenes there are.

| Where | What |
|---|---|
| YAML (`fixture-types/`) | Fixture descriptions – generic, reusable, versionable |
| SQLite (`data/lumen.db`) | Projects: universes/Art-Net, patch, groups, stage positions, scenes, fade times |
| `application.yml` | Technical settings: paths, server port, Art-Net bind address, render rate |

## Decisions

- **One database file** (`data/lumen.db`) with any number of projects. Exactly one project is *active*.
- **Export/import of a project** as a single JSON file (`<name>.lumen.json`) – as a backup and to take a show to
  another computer. In addition, Lumen automatically backs up the whole database to `data/backups/`.
- **Access:** `sqlite-jdbc` (xerial) + Spring `JdbcClient`, no JPA (SQLite and Hibernate do not fit well together,
  and the schema is small).
- **Migrations:** Flyway (`lumen-adapter-sqlite/src/main/resources/db/migration`). Every iteration only adds the
  tables it needs: V1 = projects & patch (iteration 2), V2 = scenes (iteration 6),
  V3 = `app_setting` and `playback_state` (iteration 8), V4 = `stage_plan` (iteration 9), V5 = compositions
  (iteration K, replaced by looks/compositions with V19),
  V6 = extra keys (`composition.once`, `composition.key_fade_ms`, table `scene_extra_key`),
  V7 = scenes on extra keys (`scene.key_only`, table `extra_key_scene`), V8 = fade times per scene
  (`scene.fade_in_ms`, `fade_out_ms`, `delay_ms`, iteration 10), V9 = party buttons (iteration 11),
  V10 = script per scene (`scene.script`, iteration T), V11 = network interface per universe
  (`universe.network_interface`, iteration N), V12 = expected Art-Net nodes per project (`expected_node`,
  iteration H; not part of the project export, because the nodes belong to the rig on site), …,
  V18 = shape per fixture in the stage view (`fixture.stage_shape`, `stage_rotation`, `stage_length`),
  V19 = looks, compositions, scenes in three levels (iteration S, see
  [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md)); the tables of the old model remain as `legacy_*`
  and are converted once at start (`legacy_conversion` remembers the projects),
  V20 = parallel steps (`composition_step.parallel`), V21 = faders play compositions (`fader.composition_id`,
  `scene.outside_cue_list` is dropped; for projects not yet converted, `legacy_fader` and `legacy_outside_cue_list`
  remain), V22 = party matrix (`party_matrix`, `party_cell`; `party_button*` is dropped),
  V23 = parts after light markers and looks in scenes (`scene_composition.section`, `scene_look`), …,
  V28 = wide party fields and gradients (`party_cell.span`, `party_cell.fader_kind`, `party_cell_fixture`),
  V29 = party pages and pads (`party_page` replaces `party_matrix`, `page_index` and `party_cell.row_span`),
  V30 = folders for compositions (`composition.folder`, default 'Manual'), V31 = compositions quick setup made before
  move to 'Auto generated', V32 = folders for looks (`look.folder`; quick setup's shared looks to 'Auto generated'),
  V33 = sACN per universe (`universe.protocol`, `sacn_universe`, `sacn_priority`, `sacn_targets`).
- **SQLite settings:** `journal_mode=WAL`, `foreign_keys=ON`, `synchronous=NORMAL`.
- Fixture types are referenced from the library by `fixture_type_id` + `mode_name`, not copied.

## Schema (target picture)

```
project
  id              INTEGER PK
  name            TEXT NOT NULL
  venue           TEXT                         -- venue, optional
  fade_in_ms      INTEGER NOT NULL DEFAULT 3000   -- global fade times
  fade_out_ms     INTEGER NOT NULL DEFAULT 3000
  fade_curve      TEXT NOT NULL DEFAULT 'linear'   -- 'linear', 's-curve' or 'square'
  active          INTEGER NOT NULL DEFAULT 0   -- exactly one = 1
  created_at, updated_at  TEXT (ISO-8601)

universe                                        -- replaces the former outputs.yml
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  number          INTEGER NOT NULL              -- logical number in the project (1, 2, …)
  name            TEXT
  artnet_net      INTEGER NOT NULL DEFAULT 0    -- 0–127
  artnet_subnet   INTEGER NOT NULL DEFAULT 0    -- 0–15
  artnet_universe INTEGER NOT NULL DEFAULT 0    -- 0–15
  target          TEXT NOT NULL                 -- 'auto', 'auto,10.0.0.5', '10.0.0.5,10.0.0.6' or 'broadcast'
  network_interface TEXT NULL                   -- e.g. 'en7': send only via this interface, NULL = all networks
  protocol        TEXT NOT NULL DEFAULT 'artnet' -- 'artnet', 'sacn' or 'both' (V33)
  sacn_universe   INTEGER NULL                  -- 1–63999; NULL = the number (V33)
  sacn_priority   INTEGER NOT NULL DEFAULT 100  -- 0–200 (V33)
  sacn_targets    TEXT NULL                     -- '10.0.0.5,10.0.0.6'; NULL = multicast (V33)
  UNIQUE(project_id, number)

fixture                                         -- patched instance
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  name            TEXT NOT NULL                 -- e.g. "Front left", "Spot centre stage"
  short_name      TEXT                          -- for the stage view, e.g. "FL"
  fixture_type_id TEXT NOT NULL                 -- reference into the YAML library
  mode_name       TEXT NOT NULL
  universe_id     FK → universe ON DELETE RESTRICT   -- a universe with fixtures cannot be deleted
  address         INTEGER NOT NULL              -- 1–512
  pos_x, pos_y    REAL                          -- 0..1, stage view
  sort_order      INTEGER
  stage_shape     TEXT NOT NULL DEFAULT 'auto'  -- shape in the stage view: auto | spot | bar | strip
  stage_rotation  INTEGER NOT NULL DEFAULT 0    -- 0–359°, clockwise, 0 = across the stage
  stage_length    REAL NOT NULL DEFAULT 0.15    -- length of bar/strip as a share of the stage width

fixture_group
  id, project_id, name, color, sort_order

fixture_group_member
  group_id        FK → fixture_group ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  position        INTEGER NOT NULL              -- order of the members
  PRIMARY KEY(group_id, fixture_id)

scene
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  number          TEXT NOT NULL                 -- "1", "2", "2.5" – normalized, defines the order
  name            TEXT NOT NULL                 -- "Act 1 – Dawn"
  cue_note        TEXT                          -- cue note: "GO on: '…and then the sun rose'"
  auto_follow_ms  INTEGER NULL                  -- NULL = stay until GO; otherwise automatic GO when it runs out
  fade_in_ms      INTEGER NULL                  -- fade-in at the GO into this scene, NULL = project time
  fade_out_ms     INTEGER NULL                  -- fade-out of the picture before, at the GO into this scene, NULL = project time
  delay_ms        INTEGER NOT NULL DEFAULT 0    -- the fade-in starts this long after the GO
  fade_curve      TEXT NULL                     -- own fade curve, NULL = the project's
  script          TEXT NOT NULL DEFAULT ''      -- spoken text of the scene, **…** = stage direction
  UNIQUE(project_id, number)

scene_composition                               -- compositions of a scene, order = priority
  scene_id        FK → scene ON DELETE CASCADE
  section         TEXT NOT NULL DEFAULT ''      -- '' = start of the scene, otherwise name of the light marker (V23)
  composition_id  FK → composition              -- a composition that is played cannot be deleted
  position        INTEGER NOT NULL
  level           REAL NOT NULL                 -- 0..1
  speed_factor    REAL NOT NULL
  restart         INTEGER NOT NULL              -- 1 = restart at GO instead of continuing
  PRIMARY KEY(scene_id, section, composition_id)

scene_look                                      -- looks a scene plays directly, below the compositions (V23)
  scene_id        FK → scene ON DELETE CASCADE
  section         TEXT NOT NULL DEFAULT ''      -- as for scene_composition
  look_id         FK → look                     -- a look used like this cannot be deleted
  position, level

scene_key                                       -- extra keys of a scene
  scene_id, slot (1–8), composition_id, key_mode ('toggle' | 'flash'), fade_ms   PRIMARY KEY(scene_id, slot)

composition                                     -- steps that play looks
  id, project_id, name
  fade_ms, hold_ms                              -- default times of the steps
  play_mode       TEXT NOT NULL                 -- 'loop' | 'ping_pong' | 'once'
  intensity_mode  TEXT NOT NULL                 -- 'absolute' | 'relative'
  generator_kind, generator (JSON), generator_group_id, generator_per_head   -- only for "Regenerate"
  folder          TEXT NOT NULL                 -- 'Manual', 'Auto generated' (quick setup) or own names (V30)
composition_generator_fixture                   -- fixed fixture list as the generator's target
composition_step                                -- composition_id, step_index, look_id, fade_ms/hold_ms (NULL = default),
                                                --   parallel (1 = starts with the step before, same row)

look                                            -- fixed values, normalized 0..1
  id, project_id, name
  owner_composition_id FK → composition ON DELETE CASCADE NULL   -- set = a composition's own look
  folder          TEXT NOT NULL                 -- 'Manual', 'Auto generated' (quick setup) or own names (V32)
look_value                                      -- look_id, fixture_id, attribute, value
  attribute       TEXT NOT NULL                 -- attribute ID: 'dimmer', 'red', 'pan', 'generic:Program speed', …

preset                                          -- named values of an attribute group per fixture
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  name            TEXT NOT NULL                 -- "Warm", "Chair"
  attribute_group TEXT NOT NULL                 -- 'color', 'position' or 'beam'

preset_value                                    -- like look_value, only attributes of the group
  preset_id, fixture_id, attribute, value       PRIMARY KEY(preset_id, fixture_id, attribute)

look_preset                                     -- which preset the values of a look come from
  look_id         FK → look ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  attribute_group TEXT NOT NULL
  preset_id       FK → preset ON DELETE CASCADE -- deleted preset: the values stay, the reference goes
  PRIMARY KEY(look_id, fixture_id, attribute_group)

look_revision                                   -- earlier contents of a look (undo), the newest 20
  id, look_id (FK ON DELETE CASCADE), created_at, reason
look_revision_value / look_revision_preset      -- values and preset references of the earlier content

fader                                           -- faders in the show, e.g. house lights
  project_id      FK → project ON DELETE CASCADE
  slot            INTEGER NOT NULL              -- 1–8
  name            TEXT                          -- NULL = name of the composition
  composition_id  FK → composition ON DELETE CASCADE
  PRIMARY KEY(project_id, slot)

party_page                                      -- a page (tab) of the party matrix (none = one empty 4 × 4 page)
  project_id, page_index                        -- PK; page_index 0–11
  name            TEXT NOT NULL                 -- shown on the tab
  rows, columns   INTEGER 1–12
  separators      TEXT                          -- rows with a divider above them, e.g. '2,4'

party_cell                                      -- a field; without scene and composition just text
  project_id, page_index, row_index, column_index -- PK; FK → party_page ON DELETE CASCADE
  label           TEXT NULL                     -- NULL = name of what it plays
  scene_id        FK → scene ON DELETE SET NULL
  composition_id  FK → composition ON DELETE SET NULL
  move_id         FK → move ON DELETE SET NULL
  color, border_color TEXT                      -- '#rrggbb'
  key_mode        TEXT                          -- 'toggle' | 'flash'
  group_name      TEXT NULL                     -- fields of a group exclude each other (on all pages)
  span, row_span  INTEGER 1–12                  -- columns and rows the field covers
  fader_kind      TEXT NULL                     -- 'dimmer' | 'color' | 'pan-tilt': a gradient or pad

party_cell_fixture                              -- fixtures of a gradient or pad, in order
  project_id, page_index, row_index, column_index, position -- PK; FK → party_cell ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE

app_setting                                     -- settings across projects
  key             TEXT PK                       -- 'config_pin_hash', 'network_access', 'operating_mode'
  value           TEXT NOT NULL

stage_plan                                      -- optional floor plan behind the stage view
  project_id      PK, FK → project ON DELETE CASCADE
  content_type    TEXT NOT NULL                 -- image/png, image/jpeg or image/webp (no SVG)
  data            BLOB NOT NULL                 -- at most 5 MB

playback_state                                  -- for resuming after a crash/restart
  project_id      PK, FK → project ON DELETE CASCADE
  current_scene_id FK → scene ON DELETE SET NULL   -- NULL = dark (before scene 1 or after the end)
  updated_at      TEXT NOT NULL
```

### Project file (export/import)

```json
{
  "format": "lumen-project", "version": 5, "exportedAt": "2026-10-06T19:30:00Z",
  "project":   { "name": "The Tempest", "venue": "City Theatre", "fadeInMs": 3000, "fadeOutMs": 3000 },
  "universes": [ { "id": 1, "number": 1, "name": "Stage", "artnet": { "net": 0, "subnet": 0, "universe": 0 },
                   "mode": "auto", "ipAddresses": [], "protocol": "artnet",
                   "sacn": { "universe": 1, "priority": 100, "ipAddresses": [] } } ],
  "fixtures":  [ { "id": 7, "name": "Front left", "shortName": "FL", "fixtureTypeId": "generic-rgbw-par",
                   "mode": "8ch", "universeId": 1, "address": 1, "position": null, "sortOrder": 0 } ],
  "groups":    [ { "id": 2, "name": "Front", "color": "#3cf0ff", "sortOrder": 0, "fixtureIds": [7] } ],
  "looks":     [ { "id": 11, "name": "Storm", "owner": 4,
                   "values": { "7": { "dimmer": 1.0, "blue": 0.8 } }, "presets": { "7": { "color": 3 } } } ],
  "compositions": [ { "id": 4, "name": "Storm", "fadeMs": 1000, "holdMs": 2000, "playMode": "loop",
                      "intensityMode": "absolute", "generator": null,
                      "steps": [ { "lookId": 11, "fadeMs": null, "holdMs": null, "parallel": false } ] } ],
  "scenes":    [ { "number": "2.5", "name": "Storm", "cueNote": "GO on thunder", "autoFollowMs": null,
                   "compositions": [ { "compositionId": 4, "level": 1, "speedFactor": 1, "restart": false } ],
                   "keys": [ { "slot": 1, "compositionId": 4, "mode": "flash", "fadeMs": 0 } ] } ],
  "stagePlan": { "contentType": "image/png", "data": "<Base64>" },
  "presets":   [ { "id": 3, "name": "Warm", "group": "color", "values": { "7": { "red": 1.0, "green": 0.6 } } } ],
  "faders":    [ { "slot": 1, "name": "House lights", "compositionId": 4 } ],
  "partyMatrix": { "pages": [
    { "name": "Party", "rows": 2, "columns": 4, "separators": [1],
      "cells": [ { "row": 0, "column": 0, "label": "Red", "compositionId": 4, "color": "#c01020",
                   "borderColor": "#ff4060", "mode": "toggle", "group": "Colors" },
                 { "row": 1, "column": 0, "scene": "2.5", "mode": "flash" },
                 { "row": 1, "column": 1, "span": 3, "fader": { "kind": "color", "fixtureIds": [7] } } ] },
    { "name": "Movers", "rows": 2, "columns": 2,
      "cells": [ { "row": 0, "column": 0, "span": 2, "rowSpan": 2,
                   "fader": { "kind": "pan-tilt", "fixtureIds": [9] } } ] } ] }
}
```

The IDs only connect the parts of the file. Files of version 1 (before iteration S: values directly in the scenes,
composition copies, project-wide extra keys) are converted on import like an old database. In version 2, faders
played a scene (by number) and scenes could be outside the cue list: the fader then plays the scene's first
composition, and such scenes are dropped. Party buttons of older files are ignored. Versions 3 and 4 have a party
matrix of one page (`rows`, `columns`, `separators` and `cells` directly in `partyMatrix`); it becomes the first page. An import always creates a
**new project** with new IDs; if a project of that name exists, it gets the suffix "(imported)". References to unknown
universes/fixtures are rejected without leaving half a project behind. Missing fixture types are allowed and show up
as a patch problem.

**Order = scene number** (numeric: 1 < 2 < 2.5 < 10). Inserting works through decimal numbers; there is no separate
sort column – so scene 5 can never come before scene 3. Heads of multi-pixel fixtures have their own attribute IDs
(e.g. `red@2`), which needed no schema change.

**Presets are written into the looks:** a look always stores its values completely, `look_preset` only remembers
which preset they come from. When a preset is updated, Lumen writes the new values into all looks that refer to it.
Playback, export and all other readers therefore only see finished values.

**Values are stored per attribute, not as raw DMX bytes.** If a fixture is re-patched (different address, different
mode), the looks stay valid.

**Fixtures that no composition of a scene sets** are **off** in that scene (intensity 0). So they are faded out on
the change. Their other attributes (color, position) stay as they are. Within a composition, a fixture that is
missing in a step or has no intensity there is dark in that step.

## Validation in the patch

- address + channel count − 1 ≤ 512
- no overlap in the same universe (with the names of both fixtures)
- type and mode exist in the library
- unique Art-Net port address per universe in the project
