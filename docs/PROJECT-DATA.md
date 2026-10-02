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
- **Export/Import eines Projekts** als einzelne Datei (JSON, später auch als eigene `.sqlite`) –
  als Backup und um eine Show auf einen anderen Rechner mitzunehmen.
- **Zugriff:** `sqlite-jdbc` (xerial) + Spring `JdbcClient`, kein JPA (SQLite und Hibernate passen schlecht zusammen,
  und das Schema ist überschaubar).
- **Migrationen:** Flyway (`lumen-adapter-sqlite/src/main/resources/db/migration`). Jede Iteration ergänzt nur die
  Tabellen, die sie braucht: V1 = Projekte & Patch (Iteration 2), Szenen, `app_setting` und `playback_state` folgen.
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
  target          TEXT NOT NULL                 -- IP oder 'broadcast'
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
  number          TEXT NOT NULL                 -- "1", "2", "2.5" – Einfügen ohne Umnummerieren
  name            TEXT NOT NULL                 -- "Akt 1 – Morgendämmerung"
  cue_note        TEXT                          -- Regiehinweis: "GO bei: '…und dann ging die Sonne auf'"
  auto_follow_ms  INTEGER NULL                  -- NULL = Stay bis GO; sonst automatisches GO nach Ablauf
  sort_order      INTEGER NOT NULL              -- Reihenfolge in der Szenenliste
  UNIQUE(project_id, number)

scene_value                                     -- Werte einer Szene, normalisiert 0..1
  scene_id        FK → scene ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  function        TEXT NOT NULL                 -- 'dimmer', 'red', 'pan', 'gobo', …
  head            INTEGER NOT NULL DEFAULT 0    -- für Multi-Pixel-Geräte
  value           REAL NOT NULL
  PRIMARY KEY(scene_id, fixture_id, function, head)

app_setting                                     -- projektübergreifende Einstellungen
  key             TEXT PK                       -- z.B. 'config_pin_hash'
  value           TEXT

playback_state                                  -- für Wiederaufnahme nach Absturz/Neustart
  project_id      PK, FK → project
  current_scene_id FK → scene NULL
  updated_at      TEXT
```

**Werte werden pro Funktion gespeichert, nicht als rohe DMX-Bytes.** Wird ein Gerät umgepatcht (andere Adresse,
anderer Modus), bleiben die Szenen gültig.

**Nicht in einer Szene enthaltene Fixtures** sind in dieser Szene **aus** (Intensität 0). Sie werden also beim
Wechsel ausgefadet. Ihre übrigen Attribute (Farbe, Position) bleiben, wie sie sind.

## Validierung im Patch

- Adresse + Kanalanzahl − 1 ≤ 512
- keine Überschneidung im selben Universe (mit Namen beider Fixtures)
- Typ und Modus existieren in der Bibliothek
- eindeutige Art-Net-Port-Adresse pro Universe im Projekt
