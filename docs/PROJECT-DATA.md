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
  (Iteration K, Schema in [COMPOSITIONS.md](COMPOSITIONS.md#datenmodell-entwurf-neue-flyway-migration)).
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
  UNIQUE(project_id, number)

scene_value                                     -- Werte einer Szene, normalisiert 0..1
  scene_id        FK → scene ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  attribute       TEXT NOT NULL                 -- Attribut-ID: 'dimmer', 'red', 'pan', 'generic:Program speed', …
  value           REAL NOT NULL
  PRIMARY KEY(scene_id, fixture_id, attribute)

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
  "format": "lumen-project", "version": 1, "exportedAt": "2026-10-02T19:30:00Z",
  "project":   { "name": "Der Sturm", "venue": "Stadttheater", "fadeInMs": 3000, "fadeOutMs": 3000 },
  "universes": [ { "id": 1, "number": 1, "name": "Bühne", "artnet": { "net": 0, "subnet": 0, "universe": 0 },
                   "mode": "auto", "ipAddresses": [] } ],
  "fixtures":  [ { "id": 7, "name": "Front links", "shortName": "FL", "fixtureTypeId": "generic-rgbw-par",
                   "mode": "8ch", "universeId": 1, "address": 1, "position": null, "sortOrder": 0 } ],
  "groups":    [ { "name": "Front", "color": "#3cf0ff", "sortOrder": 0, "fixtureIds": [7] } ],
  "scenes":    [ { "number": "2.5", "name": "Sturm", "cueNote": "GO bei Donner", "autoFollowMs": null,
                   "values": { "7": { "dimmer": 1.0, "blue": 0.8 } } } ],
  "stagePlan": { "contentType": "image/png", "data": "<Base64>" },
  "compositions": [ { "id": 4, "sceneNumber": null, "sourceId": null, "name": "Lauflicht", "kind": "chase",
                      "params": { "stepMs": 400 }, "target": { "groupId": 2 }, "level": 1 },
                    { "id": 9, "sceneNumber": "2.5", "sourceId": 4, "name": "Lauflicht", "kind": "chase", … } ]
}
```

Die IDs verbinden nur die Teile der Datei. Beim Import entsteht ein **neues Projekt** mit neuen IDs; heißt schon ein
Projekt so, bekommt es den Zusatz „(imported)“. Verweise auf unbekannte Universen/Fixtures werden abgelehnt, ohne
ein halbes Projekt zu hinterlassen. Fehlende Gerätetypen sind erlaubt und erscheinen als Patch-Problem.

**Reihenfolge = Szenennummer** (numerisch: 1 < 2 < 2.5 < 10). Einfügen geht über Dezimalnummern, eine separate
Sortierspalte gibt es nicht – so kann Szene 5 nie vor Szene 3 stehen. Multi-Pixel-Geräte bekommen später eigene
Attribut-IDs (z.B. `red@2`), dafür ist keine Schemaänderung nötig.

**Werte werden pro Attribut gespeichert, nicht als rohe DMX-Bytes.** Wird ein Gerät umgepatcht (andere Adresse,
anderer Modus), bleiben die Szenen gültig.

**Nicht in einer Szene enthaltene Fixtures** sind in dieser Szene **aus** (Intensität 0). Sie werden also beim
Wechsel ausgefadet. Ihre übrigen Attribute (Farbe, Position) bleiben, wie sie sind.

## Validierung im Patch

- Adresse + Kanalanzahl − 1 ≤ 512
- keine Überschneidung im selben Universe (mit Namen beider Fixtures)
- Typ und Modus existieren in der Bibliothek
- eindeutige Art-Net-Port-Adresse pro Universe im Projekt
