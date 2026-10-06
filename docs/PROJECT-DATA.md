# Projektdaten (SQLite)

Ein **Projekt** ist ein konkretes Theaterstück bzw. eine konkrete Spielstätte. Darin steht, welche Geräte
aus der Bibliothek ([FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md)) **tatsächlich verbaut** sind, wie sie gepatcht sind
und welche Szenen es gibt.

| Wo | Was |
|---|---|
| YAML (`fixture-types/`) | Gerätebeschreibungen – generisch, wiederverwendbar, versionierbar |
| SQLite (`data/lumen.db`) | Projekte: Universen/Art-Net, Patch, Gruppen, Bühnenpositionen, Szenen, Fade-Zeiten |
| `application.yml` | Technisches: Pfade, Server-Port, Art-Net-Bind-Adresse, Render-Rate |

## Entscheidungen

- **Eine Datenbankdatei** (`data/lumen.db`) mit beliebig vielen Projekten. Genau ein Projekt ist *aktiv*.
- **Export/Import eines Projekts** als einzelne JSON-Datei (`<Name>.lumen.json`) – als Backup und um eine Show auf
  einen anderen Rechner mitzunehmen. Zusätzlich sichert Lumen automatisch die ganze Datenbank nach `data/backups/`.
- **Zugriff:** `sqlite-jdbc` (xerial) + Spring `JdbcClient`, kein JPA (SQLite und Hibernate passen schlecht zusammen,
  und das Schema ist überschaubar).
- **Migrationen:** Flyway (`lumen-adapter-sqlite/src/main/resources/db/migration`). Jede Iteration ergänzt nur die
  Tabellen, die sie braucht: V1 = Projekte & Patch (Iteration 2), V2 = Szenen (Iteration 6),
  V3 = `app_setting` und `playback_state` (Iteration 8), V4 = `stage_plan` (Iteration 9), V5 = Kompositionen
  (Iteration K, Schema in [COMPOSITIONS.md](COMPOSITIONS.md#datenmodell-entwurf-neue-flyway-migration)),
  V6 = Extra-Tasten (`composition.once`, `composition.key_fade_ms`, Tabelle `scene_extra_key`),
  V7 = Szenen auf Extra-Tasten (`scene.key_only`, Tabelle `extra_key_scene`), V8 = Fade-Zeiten pro Szene
  (`scene.fade_in_ms`, `fade_out_ms`, `delay_ms`, Iteration 10), V9 = Party-Buttons (Iteration 11),
  V10 = Textbuch pro Szene (`scene.script`, Iteration T), V11 = Netzwerk-Interface pro Universe
  (`universe.network_interface`, Iteration N), V12 = erwartete Art-Net-Nodes pro Projekt (`expected_node`,
  Iteration H; reist nicht im Projekt-Export mit, weil die Nodes zum Aufbau vor Ort gehören), …,
  V18 = Form pro Fixture in der Bühnenansicht (`fixture.stage_shape`, `stage_rotation`, `stage_length`),
  V19 = Looks, Compositions, Szenen in drei Stufen (Iteration S, siehe
  [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md)); die Tabellen des alten Modells bleiben als `legacy_*`
  erhalten und werden beim Start einmal umgewandelt (`legacy_conversion` merkt sich die Projekte).
- **SQLite-Settings:** `journal_mode=WAL`, `foreign_keys=ON`, `synchronous=NORMAL`.
- Fixture-Typen werden per `fixture_type_id` + `mode_name` aus der Bibliothek referenziert, nicht kopiert.

## Schema (Zielbild)

```
project
  id              INTEGER PK
  name            TEXT NOT NULL
  venue           TEXT                         -- Spielstätte, optional
  fade_in_ms      INTEGER NOT NULL DEFAULT 3000   -- globale Fade-Zeiten
  fade_out_ms     INTEGER NOT NULL DEFAULT 3000
  fade_curve      TEXT NOT NULL DEFAULT 'linear'   -- 'linear', 's-curve' oder 'square'
  active          INTEGER NOT NULL DEFAULT 0   -- genau eins = 1
  created_at, updated_at  TEXT (ISO-8601)

universe                                        -- ersetzt die frühere outputs.yml
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  number          INTEGER NOT NULL              -- logische Nummer im Projekt (1, 2, …)
  name            TEXT
  artnet_net      INTEGER NOT NULL DEFAULT 0    -- 0–127
  artnet_subnet   INTEGER NOT NULL DEFAULT 0    -- 0–15
  artnet_universe INTEGER NOT NULL DEFAULT 0    -- 0–15
  target          TEXT NOT NULL                 -- 'auto', 'auto,10.0.0.5', '10.0.0.5,10.0.0.6' oder 'broadcast'
  network_interface TEXT NULL                   -- z.B. 'en7': nur über dieses Interface senden, NULL = alle Netze
  UNIQUE(project_id, number)

fixture                                         -- gepatchte Instanz
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  name            TEXT NOT NULL                 -- z.B. "Front links", "Spot Bühnenmitte"
  short_name      TEXT                          -- für die Bühnenansicht, z.B. "FL"
  fixture_type_id TEXT NOT NULL                 -- Referenz in die YAML-Bibliothek
  mode_name       TEXT NOT NULL
  universe_id     FK → universe ON DELETE RESTRICT   -- Universe mit Geräten kann nicht gelöscht werden
  address         INTEGER NOT NULL              -- 1–512
  pos_x, pos_y    REAL                          -- 0..1, Bühnenansicht
  sort_order      INTEGER
  stage_shape     TEXT NOT NULL DEFAULT 'auto'  -- Form in der Bühnenansicht: auto | spot | bar | strip
  stage_rotation  INTEGER NOT NULL DEFAULT 0    -- 0–359°, im Uhrzeigersinn, 0 = quer zur Bühne
  stage_length    REAL NOT NULL DEFAULT 0.15    -- Länge von Bar/Strip als Anteil der Bühnenbreite

fixture_group
  id, project_id, name, color, sort_order

fixture_group_member
  group_id        FK → fixture_group ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  position        INTEGER NOT NULL              -- Reihenfolge der Mitglieder
  PRIMARY KEY(group_id, fixture_id)

scene
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  number          TEXT NOT NULL                 -- "1", "2", "2.5" – normalisiert, bestimmt die Reihenfolge
  name            TEXT NOT NULL                 -- "Akt 1 – Morgendämmerung"
  cue_note        TEXT                          -- Regiehinweis: "GO bei: '…und dann ging die Sonne auf'"
  auto_follow_ms  INTEGER NULL                  -- NULL = Stay bis GO; sonst automatisches GO nach Ablauf
  outside_cue_list INTEGER NOT NULL DEFAULT 0   -- 1 = GO überspringt die Szene (für Fader); bis V18 key_only
  fade_in_ms      INTEGER NULL                  -- Fade-In beim GO in diese Szene, NULL = Projektzeit
  fade_out_ms     INTEGER NULL                  -- Fade-Out des Bildes davor beim GO in diese Szene, NULL = Projektzeit
  delay_ms        INTEGER NOT NULL DEFAULT 0    -- Fade-In startet so lange nach dem GO
  fade_curve      TEXT NULL                     -- eigene Fade-Kurve, NULL = die des Projekts
  script          TEXT NOT NULL DEFAULT ''      -- gesprochener Text der Szene, **…** = Regieanweisung
  UNIQUE(project_id, number)

scene_composition                               -- Compositions einer Szene, Reihenfolge = Priorität
  scene_id        FK → scene ON DELETE CASCADE
  composition_id  FK → composition              -- eine gespielte Composition kann nicht gelöscht werden
  position        INTEGER NOT NULL
  level           REAL NOT NULL                 -- 0..1
  speed_factor    REAL NOT NULL
  restart         INTEGER NOT NULL              -- 1 = beim GO neu starten statt weiterlaufen
  PRIMARY KEY(scene_id, composition_id)

scene_key                                       -- Extra-Tasten einer Szene
  scene_id, slot (1–8), composition_id, key_mode ('toggle' | 'flash'), fade_ms   PRIMARY KEY(scene_id, slot)

composition                                     -- Schritte, die Looks spielen
  id, project_id, name
  fade_ms, hold_ms                              -- Standardzeiten der Schritte
  play_mode       TEXT NOT NULL                 -- 'loop' | 'ping_pong' | 'once'
  intensity_mode  TEXT NOT NULL                 -- 'absolute' | 'relative'
  generator_kind, generator (JSON), generator_group_id, generator_per_head   -- nur für „Regenerate“
composition_generator_fixture                   -- feste Fixture-Liste als Ziel des Generators
composition_step                                -- composition_id, step_index, look_id, fade_ms/hold_ms (NULL = Standard)

look                                            -- feste Werte, normalisiert 0..1
  id, project_id, name
  owner_composition_id FK → composition ON DELETE CASCADE NULL   -- gesetzt = eigener Look einer Composition
look_value                                      -- look_id, fixture_id, attribute, value
  attribute       TEXT NOT NULL                 -- Attribut-ID: 'dimmer', 'red', 'pan', 'generic:Program speed', …

preset                                          -- benannte Werte einer Attributgruppe pro Fixture
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  name            TEXT NOT NULL                 -- "Warm", "Stuhl"
  attribute_group TEXT NOT NULL                 -- 'color', 'position' oder 'beam'

preset_value                                    -- wie look_value, nur Attribute der Gruppe
  preset_id, fixture_id, attribute, value       PRIMARY KEY(preset_id, fixture_id, attribute)

look_preset                                     -- aus welchem Preset die Werte eines Looks stammen
  look_id         FK → look ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  attribute_group TEXT NOT NULL
  preset_id       FK → preset ON DELETE CASCADE -- gelöschtes Preset: die Werte bleiben, der Verweis geht
  PRIMARY KEY(look_id, fixture_id, attribute_group)

look_revision                                   -- frühere Inhalte eines Looks (Rückgängig), die neuesten 20
  id, look_id (FK ON DELETE CASCADE), created_at, reason
look_revision_value / look_revision_preset      -- Werte und Preset-Verweise des früheren Inhalts

fader                                           -- Fader im Betrieb, z.B. Saallicht
  project_id      FK → project ON DELETE CASCADE
  slot            INTEGER NOT NULL              -- 1–8
  name            TEXT                          -- NULL = Name der Szene
  scene_id        FK → scene ON DELETE CASCADE
  PRIMARY KEY(project_id, slot)

app_setting                                     -- projektübergreifende Einstellungen
  key             TEXT PK                       -- 'config_pin_hash', 'network_access', 'operating_mode'
  value           TEXT NOT NULL

stage_plan                                      -- optionaler Grundriss hinter der Bühnenansicht
  project_id      PK, FK → project ON DELETE CASCADE
  content_type    TEXT NOT NULL                 -- image/png, image/jpeg oder image/webp (kein SVG)
  data            BLOB NOT NULL                 -- höchstens 5 MB

playback_state                                  -- für Wiederaufnahme nach Absturz/Neustart
  project_id      PK, FK → project ON DELETE CASCADE
  current_scene_id FK → scene ON DELETE SET NULL   -- NULL = dunkel (vor Szene 1 oder nach dem Ende)
  updated_at      TEXT NOT NULL
```

### Projektdatei (Export/Import)

```json
{
  "format": "lumen-project", "version": 2, "exportedAt": "2026-10-06T19:30:00Z",
  "project":   { "name": "Der Sturm", "venue": "Stadttheater", "fadeInMs": 3000, "fadeOutMs": 3000 },
  "universes": [ { "id": 1, "number": 1, "name": "Bühne", "artnet": { "net": 0, "subnet": 0, "universe": 0 },
                   "mode": "auto", "ipAddresses": [] } ],
  "fixtures":  [ { "id": 7, "name": "Front links", "shortName": "FL", "fixtureTypeId": "generic-rgbw-par",
                   "mode": "8ch", "universeId": 1, "address": 1, "position": null, "sortOrder": 0 } ],
  "groups":    [ { "id": 2, "name": "Front", "color": "#3cf0ff", "sortOrder": 0, "fixtureIds": [7] } ],
  "looks":     [ { "id": 11, "name": "Sturm", "owner": 4,
                   "values": { "7": { "dimmer": 1.0, "blue": 0.8 } }, "presets": { "7": { "color": 3 } } } ],
  "compositions": [ { "id": 4, "name": "Sturm", "fadeMs": 1000, "holdMs": 2000, "playMode": "loop",
                      "intensityMode": "absolute", "generator": null,
                      "steps": [ { "lookId": 11, "fadeMs": null, "holdMs": null } ] } ],
  "scenes":    [ { "number": "2.5", "name": "Sturm", "cueNote": "GO bei Donner", "autoFollowMs": null,
                   "compositions": [ { "compositionId": 4, "level": 1, "speedFactor": 1, "restart": false } ],
                   "keys": [ { "slot": 1, "compositionId": 4, "mode": "flash", "fadeMs": 0 } ] } ],
  "stagePlan": { "contentType": "image/png", "data": "<Base64>" },
  "presets":   [ { "id": 3, "name": "Warm", "group": "color", "values": { "7": { "red": 1.0, "green": 0.6 } } } ],
  "faders":    [ { "slot": 1, "name": "Saallicht", "scene": "90" } ]
}
```

Die IDs verbinden nur die Teile der Datei. Dateien der Version 1 (vor Iteration S: Werte direkt in den Szenen,
Kompositions-Kopien, projektweite Extra-Tasten) werden beim Import wie eine alte Datenbank umgewandelt. Beim Import
entsteht ein **neues Projekt** mit neuen IDs; heißt schon ein
Projekt so, bekommt es den Zusatz „(imported)“. Verweise auf unbekannte Universen/Fixtures werden abgelehnt, ohne
ein halbes Projekt zu hinterlassen. Fehlende Gerätetypen sind erlaubt und erscheinen als Patch-Problem.

**Reihenfolge = Szenennummer** (numerisch: 1 < 2 < 2.5 < 10). Einfügen geht über Dezimalnummern, eine separate
Sortierspalte gibt es nicht – so kann Szene 5 nie vor Szene 3 stehen. Köpfe von Multi-Pixel-Geräten haben eigene
Attribut-IDs (z.B. `red@2`), dafür war keine Schemaänderung nötig.

**Presets werden in die Looks geschrieben:** Ein Look speichert die Werte immer vollständig, `look_preset` merkt
sich nur, aus welchem Preset sie stammen. Wird ein Preset aktualisiert, schreibt Lumen die neuen Werte in alle Looks,
die darauf verweisen. Playback, Export und alle anderen Leser sehen deshalb nur fertige Werte.

**Werte werden pro Attribut gespeichert, nicht als rohe DMX-Bytes.** Wird ein Gerät umgepatcht (andere Adresse,
anderer Modus), bleiben die Looks gültig.

**Fixtures, die keine Composition einer Szene setzt,** sind in dieser Szene **aus** (Intensität 0). Sie werden also beim
Wechsel ausgefadet. Ihre übrigen Attribute (Farbe, Position) bleiben, wie sie sind. Innerhalb einer Composition ist ein
Fixture, das in einem Schritt fehlt oder dort keine Intensität hat, in diesem Schritt dunkel.

## Validierung im Patch

- Adresse + Kanalanzahl − 1 ≤ 512
- keine Überschneidung im selben Universe (mit Namen beider Fixtures)
- Typ und Modus existieren in der Bibliothek
- eindeutige Art-Net-Port-Adresse pro Universe im Projekt
