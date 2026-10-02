# Architektur

## Überblick: Hexagonal (Ports & Adapters)

```
                    ┌────────────────────────────────────────────────┐
  Driving Adapters  │                  Application                   │  Driven Adapters
  (eingehend)       │                                                │  (ausgehend)
                    │   Inbound Ports           Outbound Ports       │
 ┌──────────────┐   │  ┌────────────────┐     ┌───────────────────┐  │   ┌──────────────────┐
 │ REST API     │──▶│  │ ProjectUseCase │     │ DmxOutputPort     │──│──▶│ Art-Net UDP      │
 └──────────────┘   │  │ PatchUseCase   │     └───────────────────┘  │   └──────────────────┘
 ┌──────────────┐   │  │ Programmer-    │     ┌───────────────────┐  │   ┌──────────────────┐
 │ WebSocket    │──▶│  │  UseCase       │     │ FixtureLibrary    │──│──▶│ YAML-Ordner      │
 └──────────────┘   │  │ SceneUseCase   │     └───────────────────┘  │   └──────────────────┘
 ┌──────────────┐   │  │ PlaybackUseCase│     ┌───────────────────┐  │   ┌──────────────────┐
 │ MIDI (USB)   │──▶│  │ MidiInput-     │     │ MidiFeedbackPort  │──│──▶│ MIDI-LEDs        │
 └──────────────┘   │  │  UseCase       │     └───────────────────┘  │   └──────────────────┘
                    │  │                │     ┌───────────────────┐  │   ┌──────────────────┐
                    │  └───────┬────────┘     │ ProjectRepository │──│──▶│ SQLite           │
                    │          ▼              │ SceneRepository   │  │   │ (Flyway, JDBC)   │
                    │    ┌──────────┐         └───────────────────┘  │   └──────────────────┘
                    │    │  Domain  │         ┌───────────────────┐  │   ┌──────────────────┐
                    │    └──────────┘         │ StateNotifier     │──│──▶│ WebSocket-Push   │
                    │                         └───────────────────┘  │   └──────────────────┘
                    └────────────────────────────────────────────────┘
```

**Regel:** Abhängigkeiten zeigen immer nach innen. Die Domain kennt weder Spring noch YAML, SQL oder UDP.
Das sichern **ArchUnit**-Tests ab.

## Maven-Module

```
lumen/
├── pom.xml                       Parent, Versionen, Java 26
├── lumen-domain/                 reines Java, keine Abhängigkeiten
├── lumen-application/            Use Cases + Port-Interfaces, hängt nur von domain ab
├── lumen-adapter-artnet/         DmxOutputPort → Art-Net UDP
├── lumen-adapter-library-yaml/   FixtureLibrary → YAML-Ordner
├── lumen-adapter-sqlite/         ProjectRepository, SceneRepository → SQLite (Flyway + JdbcClient)
├── lumen-adapter-web/            REST + WebSocket Controller, DTOs
├── lumen-adapter-midi/           (optional, später) USB-MIDI-Controller via javax.sound.midi, siehe MIDI.md
├── lumen-frontend/               Svelte/Vite, Build landet als Static Resources im JAR
└── lumen-app/                    Spring Boot Main, Verdrahtung, application.yml
```

Adapter- und Frontend-Module werden erst in der Iteration angelegt, in der sie gebraucht werden.

Nur `lumen-app` und die Adapter kennen Spring. Die Use Cases werden in `lumen-app` als Beans verdrahtet,
damit `lumen-application` frameworkfrei bleibt.

## Datenquellen

| Quelle | Inhalt | Doku |
|---|---|---|
| YAML-Ordner `fixture-types/` | Gerätebibliothek (was ein Gerät kann) | [FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md) |
| SQLite `data/lumen.db` | Projekte: Universen, Patch, Gruppen, Szenen, Fade-Zeiten | [PROJECT-DATA.md](PROJECT-DATA.md) |
| `application.yml` | Technisches: Pfade, Port, Art-Net-Interface, Render-Rate | – |

## Domain-Modell

```
── Bibliothek ───────────────────────────────────────────────────────────
FixtureType            Gerätemodell (aus YAML)
 └─ modes: List<Mode>
      └─ channels: List<ChannelDefinition>  (function, resolution 8/16, default, ranges, fadeBehavior)

── Projekt ──────────────────────────────────────────────────────────────
Project                name, fadeIn, fadeOut (Duration), universes, fixtures, groups, scenes
Universe               Nummer, Art-Net-Port-Adresse (net/subnet/universe), Ziel (IP | broadcast)
PatchedFixture         name, FixtureType + Mode, DmxAddress(universe, 1..512), Position(x, y)
Group                  Menge von PatchedFixtures
Scene                  number ("2.5"), name, cueNote, Map<FixtureId, AttributeValues>
AttributeValues        function → normalisierter Wert (0..1)

── Laufzeit ─────────────────────────────────────────────────────────────
OutputLayer            Schnittstelle: liefert pro Tick Attributwerte + Priorität
LayerMerger            kombiniert alle Layer (HTP/LTP)
Programmer             Live-Werte im Konfigurationsmodus (ein OutputLayer)
Playback               Szenenliste, aktuelle/nächste Szene, laufende Überblendung
Crossfade              Snapshot (alt) + Zielszene (neu), Startzeit, Pause-Status
OutputState            berechnete Attributwerte aller Fixtures
DmxFrame               512 Byte pro Universe
```

Wichtige Domain-Logik (alles unit-testbar ohne Spring, Zeit über eine injizierte `Clock`):
- **Patch-Validierung:** Überschneidungen, Kanal > 512, fehlender Typ/Modus
- **Mischen beim Szenenwechsel:** HTP für Intensität, LTP für die übrigen Attribute, siehe [PLAYBACK.md](PLAYBACK.md)
- **Mapping OutputState → DmxFrame:** Funktionen auf Kanäle/Offsets, 16 Bit, virtueller Dimmer, `invert`
- **Grand Master & Blackout** als letzte Stufe vor der Ausgabe

## DMX-Engine

- **Render-Loop** (`ScheduledExecutorService`, ~40 Hz, konfigurierbar; Art-Net max. ~44 Hz).
- Pro Tick: Alle aktiven **Ausgabe-Ebenen** (`OutputLayer`) liefern Attributwerte → **Layer-Merger**
  (Intensität HTP, Rest LTP/Priorität) → `OutputState` → Master/Blackout → `DmxFrame`s → `DmxOutputPort`.
- Ebenen im Theater-Modus: `Programmer` und `Playback` (inkl. Fade-Fortschritt). Der spätere
  [Party-Modus](PARTY-MODE.md) bringt weitere Ebenen (Executors, Effekte) mit, ohne dass die Engine umgebaut werden muss.
- Gesendet werden alle Universen des aktiven Projekts, kontinuierlich (viele Nodes erwarten regelmäßige Pakete).
- Thread-Sicherheit: immutable Zustände, Austausch per `AtomicReference`. Befehle (GO, Wertänderung) kommen
  über eine Queue in den Loop.
- **Stabilität geht vor:** Eine Exception im Tick darf den Loop nicht beenden (loggen, letzten gültigen Frame
  weiter senden).

## Art-Net Adapter

- UDP Port **6454**, Paket **ArtDmx** (OpCode `0x5000`, Protokollversion 14), 512 Byte Daten.
- 15-Bit-Port-Adresse: `Net (7) | SubNet (4) | Universe (4)`.
- Ziel pro Universe: **Unicast** an die Node-IP oder **Broadcast**.
- Sequence-Nummer pro Universe (1–255).
- Später: **ArtPoll/ArtPollReply** zur Discovery, Nodes im Konfigurationsmodus auswählbar.
- Gesendet wird von einem zufälligen lokalen Port; Ziel-Port, Bind-Adresse und Broadcast-Adresse stehen in
  `application.yml` unter `lumen.artnet.*`. In Tests ist die Ausgabe abgeschaltet (`lumen.artnet.enabled: false`).

### Testen ohne Hardware

- **DMX-Werte ansehen:** `GET /api/output/universes/1/dmx` liefert die 512 Kanalwerte des aktiven Projekts.
- **Art-Net mitschneiden:** Wireshark mit dem Filter `artnet` (Port 6454) zeigt die ArtDmx-Pakete samt Kanalwerten.
- **Art-Net-Monitor:** QLC+ (Input/Output → Art-Net als Eingang, dann „DMX Monitor“). Läuft QLC+ auf demselben
  Rechner, das Universe in Lumen auf Unicast `127.0.0.1` stellen.
- **Schnelltest per curl** (aktives Projekt mit einem gepatchten Gerät vorausgesetzt):
  ```bash
  curl -X PATCH -H 'Content-Type: application/json' \
       -d '{"dimmer": 1, "red": 1}' http://127.0.0.1:8080/api/programmer/fixtures/1
  ```

## Web-API

**Konfiguration**

| Endpunkt | Zweck |
|---|---|
| `GET /api/library/fixture-types`, `GET /api/library/issues` | Gerätebibliothek |
| `CRUD /api/projects`, `GET /api/projects/active`, `POST /api/projects/{id}/activate` | Projekte verwalten, inkl. globaler Fade-Zeiten (`fadeInMs`, `fadeOutMs`) |
| `GET /api/projects/{id}/export`, `POST /api/projects/import` | Backup / Mitnehmen |
| `CRUD /api/projects/{id}/universes` | Universen & Art-Net-Ziele |
| `CRUD /api/projects/{id}/fixtures`, `GET /api/projects/{id}/patch/issues` | Patch |
| `CRUD /api/projects/{id}/groups` | Gruppen |
| `CRUD /api/projects/{id}/scenes` | Szenenliste, sortiert nach Nummer; `POST` mit `"record": true` übernimmt den Programmer, ohne `number` die nächste ganze Zahl |
| `POST /api/projects/{id}/scenes/{sceneId}/record` · `/load` | Szene mit dem Programmer überschreiben · Szene in den Programmer laden |

Projektbezogene Endpunkte nennen die Projekt-ID immer explizit im Pfad (kein versteckter „aktuelles Projekt“-Zustand).
`CRUD` = `GET` (Liste), `POST` (anlegen), `PUT /{id}` (ersetzen), `DELETE /{id}`.
Fehler kommen als Problem Details (RFC 9457): 400 mit `errors`-Liste, 404, 409.
| `GET /api/programmer` · `PATCH /api/programmer/fixtures/{id}` · `DELETE /api/programmer/fixtures/{id}` · `DELETE /api/programmer` | Live-Werte (aktives Projekt) |
| `PATCH /api/programmer/fixtures` `{fixtureIds, values}` · `POST /api/programmer/clear` `{fixtureIds}` | Auswahl setzen/löschen; jedes Gerät bekommt nur seine Attribute |
| `PATCH /api/programmer/groups/{id}` | Live-Werte für alle Mitglieder einer Gruppe |

**Betrieb**

| Endpunkt | Zweck |
|---|---|
| `POST /api/playback/go` · `/back` · `/pause` · `/finish` | Show steuern |
| `POST /api/playback/next` `{ sceneId }` | nächste Szene vormerken (Springen) |
| `GET/PUT /api/output/master` `{level}` · `PUT /api/output/blackout` `{active}` | Grand Master / Blackout |
| `GET /api/output/universes/{n}/dmx` | Roh-DMX (Monitor), `n` = Universe-Nummer im aktiven Projekt |

**Attributwerte** sind normalisiert (0.0–1.0) und werden über die Attribut-ID adressiert, z.B.
`{"dimmer": 1, "red": 0.5, "generic:Program speed": 0.2}`. Generic-Kanäle heißen `generic:<name>`.

**WebSocket `/ws`:** Der Server sammelt Änderungen und schickt sie höchstens alle 33 ms (JSON):

| Richtung | Nachricht |
|---|---|
| Server → Client | `{"type":"programmer","values":{…}}` (alle Werte), `{"type":"master","level":…,"blackout":…}`, `{"type":"patch"}` / `{"type":"projects"}` (Client lädt per REST neu), `{"type":"error","message":…}` |
| Client → Server | `{"type":"programmer.set","fixtureIds":[…],"values":{…}}`, `{"type":"master.set","level":…}`, `{"type":"blackout.set","active":…}` |

Beim Verbinden bekommt ein Client sofort Programmer und Master. Später kommen Playback-Status (aktuelle/nächste Szene,
Fade-Fortschritt) dazu. Intern veröffentlicht die Application-Schicht Änderungen über `StateEvents`; der Web-Adapter
abonniert sie – so bleibt die Abhängigkeitsrichtung nach innen.

## Betriebssicherheit (Theater!)

- Die Ausgabe läuft unabhängig vom Browser: Schließt sich der Tab oder fällt das WLAN aus, bleibt das Licht stehen.
- Die aktuelle Szene wird persistiert, nach einem Neustart kann die Show dort fortgesetzt werden.
- Automatisches DB-Backup beim Projektwechsel und beim Start des Betriebsmodus (`data/backups/`).

## Betrieb & Zugriff

- Zielplattform ist ein **Laptop am Regieplatz**: Server, Browser und MIDI-Controller laufen auf demselben Gerät,
  der Art-Net-Node hängt per Netzwerkkabel dran.
- Der HTTP-Server lauscht standardmäßig nur auf **`127.0.0.1`**. Zugriff von Tablet/Handy im Netz wird per
  Einstellung (`lumen.network-access=true` bzw. im UI) freigeschaltet.
- **Konfigurations-PIN:** Der Betriebsmodus ist ohne Login nutzbar. Alle schreibenden Konfigurations-Endpunkte
  (Patch, Szenen, Programmer, Einstellungen) erfordern eine Session, die mit der PIN entsperrt wurde. Die PIN liegt
  gehasht (z.B. Argon2/BCrypt) in `app_setting`. Umsetzung mit Spring Security im Web-Adapter.
