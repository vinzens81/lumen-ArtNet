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
- Ziel pro Universe:
  - **Auto** (Standard): Unicast an alle Nodes, die dieses Universe in ihrem ArtPollReply melden, plus optionale
    manuelle IPs (für Nodes ohne ArtPoll-Unterstützung)
  - **Feste IPs**: Unicast nur an die eingetragenen Adressen
  - **Broadcast**: an alle – jedes Gerät bekommt jedes Universe; im WLAN unzuverlässig
- Sequence-Nummer pro Universe (1–255).
- **Discovery:** ArtPoll alle 3 s an die Broadcast-Adresse; Nodes antworten per ArtPollReply an Port **6454**
  des Controllers. Lumen lauscht dort (mit `SO_REUSEADDR`/`SO_REUSEPORT`, damit z.B. QLC+ parallel laufen kann).
  Ein Node gilt 10 s nach seiner letzten Antwort als offline und wird nach 5 min vergessen. Lässt sich der Port
  nicht öffnen, bleibt die Discovery leer und der Rest läuft weiter.
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
| `GET /api/projects/{id}/export`, `POST /api/projects/import` | Projekt als JSON-Datei (Format siehe [PROJECT-DATA.md](PROJECT-DATA.md)) / als neues Projekt importieren |
| `GET /api/backups` · `POST /api/backups` | DB-Backups auflisten (inkl. Ordner) · sofort ein Backup anlegen |
| `GET /api/access` · `POST /api/access/unlock` `{pin}` · `/lock` | PIN-Status dieses Browsers, entsperren (403 falsche PIN, 429 nach 5 Fehlversuchen), sperren |
| `PUT /api/access/pin` `{pin\|null}` · `PUT /api/access/network` `{enabled}` | PIN setzen/ändern/entfernen · Netzwerkzugriff (wirkt nach Neustart) |
| `CRUD /api/projects/{id}/universes` | Universen & Art-Net-Ziele: `mode` (`auto`/`unicast`/`broadcast`) + `ipAddresses` |
| `PUT /api/projects/{id}/fixtures/positions` `{positions: {fixtureId: {x, y} \| null}}` | Bühnenpositionen (0–1) mehrerer Fixtures auf einmal, `null` = nicht auf der Bühne |
| `GET/PUT/DELETE /api/projects/{id}/stage/plan` | Grundriss-Bild (PUT mit `Content-Type: image/png\|jpeg\|webp` als Body) |
| `CRUD /api/projects/{id}/compositions` · `/scenes/{sceneId}/compositions` | Kompositions-Bibliothek und Kopien in Szenen, siehe [COMPOSITIONS.md](COMPOSITIONS.md#umsetzung-k1) |
| `POST /api/projects/{id}/compositions/{cid}/stage-preview` · `POST …/scenes/{sid}/stage-preview` · `GET/DELETE /api/stage-preview` | Komposition oder ganze Szene zum Ausprobieren auf der Bühne zeigen (nur Konfigurationsmodus) |
| `GET /api/projects/{id}/extra-keys` · `PUT/DELETE …/extra-keys/{slot}` · `GET/PUT …/scenes/{sid}/extra-keys` | Extra-Tasten belegen (`{libraryId, mode, fadeMs}`) und pro Szene freischalten (`{slots}`) |
| `POST /api/library/reload` | Gerätebibliothek neu einlesen (passiert sonst automatisch nach Dateiänderungen) |
| `GET /api/artnet/nodes` | Per ArtPoll gefundene Nodes (IP, Name, gemeldete Universen, online) |
| `CRUD /api/projects/{id}/fixtures`, `GET /api/projects/{id}/patch/issues` | Patch |
| `CRUD /api/projects/{id}/groups` | Gruppen |
| `CRUD /api/projects/{id}/scenes` | Szenenliste, sortiert nach Nummer; `POST` mit `"record": true` übernimmt den Programmer, ohne `number` die nächste ganze Zahl |
| `POST /api/projects/{id}/scenes/{sceneId}/record` · `/load` | Szene mit dem Programmer überschreiben · Szene in den Programmer laden |

Projektbezogene Endpunkte nennen die Projekt-ID immer explizit im Pfad (kein versteckter „aktuelles Projekt“-Zustand).
`CRUD` = `GET` (Liste), `POST` (anlegen), `PUT /{id}` (ersetzen), `DELETE /{id}`.
Fehler kommen als Problem Details (RFC 9457): 400 mit `errors`-Liste, 403 mit `"locked": true` (PIN nötig), 404, 409.
| `GET /api/programmer` · `PATCH /api/programmer/fixtures/{id}` · `DELETE /api/programmer/fixtures/{id}` · `DELETE /api/programmer` | Live-Werte (aktives Projekt) |
| `PATCH /api/programmer/fixtures` `{fixtureIds, values}` · `POST /api/programmer/clear` `{fixtureIds}` | Auswahl setzen/löschen; jedes Gerät bekommt nur seine Attribute |
| `PATCH /api/programmer/groups/{id}` | Live-Werte für alle Mitglieder einer Gruppe |

**Betrieb**

| Endpunkt | Zweck |
|---|---|
| `GET /api/playback` · `POST /api/playback/go` · `/back` · `/pause` · `/finish` | Show steuern, Status |
| `POST /api/playback/keys/{slot}/press` · `/release` | Extra-Taste 1–8 drücken / loslassen (Toggle bzw. Flash) |
| `POST /api/playback/resume` · `DELETE /api/playback/resume` | nach einem Neustart bei `resumeSceneId` weitermachen · Angebot verwerfen |
| `PUT /api/playback/next` `{ sceneId }` | nächste Szene vormerken (Springen) |
| `GET/PUT /api/mode` `{ mode: setup\|show }` | Betriebsmodus; `show` leert und sperrt den Programmer, zurück nach `setup` nur entsperrt |
| `GET/PUT /api/output/master` `{level}` · `PUT /api/output/blackout` `{active}` | Grand Master / Blackout |
| `GET /api/output/universes/{n}/dmx` | Roh-DMX (Monitor), `n` = Universe-Nummer im aktiven Projekt |
| `GET /api/output/health` | Render-Loop und Art-Net der letzten Sekunde: `level` (ok/warning/error), `problems`, Raten |

**Attributwerte** sind normalisiert (0.0–1.0) und werden über die Attribut-ID adressiert, z.B.
`{"dimmer": 1, "red": 0.5, "generic:Program speed": 0.2}`. Generic-Kanäle heißen `generic:<name>`.

**WebSocket `/ws`:** Der Server sammelt Änderungen und schickt sie höchstens alle 33 ms (JSON):

| Richtung | Nachricht |
|---|---|
| Server → Client | `{"type":"programmer","values":{…}}` (alle Werte), `{"type":"master","level":…,"blackout":…}`, `{"type":"playback","status":{…}}`, `{"type":"mode","mode":…}`, `{"type":"health","health":{…}}` (jede Sekunde), `{"type":"patch"}` / `{"type":"scenes"}` / `{"type":"nodes"}` / `{"type":"projects"}` / `{"type":"access"}` / `{"type":"library"}` (Client lädt per REST neu); nach einem Abo
`{"type":"output","values":{…}}` (Werte wie gesendet, vor dem Master) und `{"type":"dmx","universe":n,"values":[…512]}`, `{"type":"error","message":…}` |
| Client → Server | `{"type":"programmer.set","fixtureIds":[…],"values":{…}}`, `{"type":"master.set","level":…}`, `{"type":"blackout.set","active":…}`, `{"type":"playback.go"}` (ebenso `.back`, `.pause`, `.finish`), `{"type":"keys.press","slot":1}` (ebenso `keys.release`), `{"type":"subscribe","topics":["output","dmx:1","preview:7"]}` (ersetzt das Abo; Live-Ausgabe höchstens alle 100 ms und nur bei Änderung; `preview:<id>` liefert `{"type":"preview","compositionId":…,"values":{…}}` für den Editor) |

Beim Verbinden bekommt ein Client sofort Programmer, Master, Playback-Status, Modus und Health. `programmer.set` braucht
bei gesetzter PIN einen entsperrten Browser; nach dem Entsperren verbindet sich der Client neu. Den Fade-Fortschritt
interpoliert der Client selbst (der Status enthält die verstrichene Zeit beim Senden). Intern veröffentlicht die Application-Schicht Änderungen über `StateEvents`; der Web-Adapter
abonniert sie – so bleibt die Abhängigkeitsrichtung nach innen.

## Betriebssicherheit (Theater!)

- Die Ausgabe läuft unabhängig vom Browser: Schließt sich der Tab oder fällt das WLAN aus, bleibt das Licht stehen.
- Die aktuelle Szene wird persistiert (`playback_state`), ebenso der Betriebsmodus (`app_setting`). Nach einem
  Neustart startet Lumen im selben Modus, das Licht bleibt dunkel und der Betriebsmodus bietet an, bei der
  gespeicherten Szene weiterzumachen. Gespeichert wird auf einem eigenen Thread, der Render-Loop wartet nie auf die DB.
- Automatisches DB-Backup (`VACUUM INTO`, konsistent im laufenden Betrieb) beim Start, beim Projektwechsel und beim
  Start des Betriebsmodus nach `data/backups/`; die neuesten 30 bleiben (`lumen.backup.*`). Wiederherstellen: Lumen
  beenden und die Datei über `data/lumen.db` kopieren.
- **Watchdog:** Der Render-Loop fängt jeden Fehler im Tick ab (auch `Error`). Hängt ein Tick länger als 2 s, ersetzt
  der Watchdog den Render-Thread. Jede Sekunde werden Render-Rate und Art-Net-Pakete/s gemessen und an alle Clients
  geschickt; die Kopfleiste zeigt den Zustand (z.B. „Art-Net 40/s“, Warnung bei Sendefehlern oder ohne Empfänger).
- **Durchlauftest:** `ShowSoakTest` spielt 3 h Show (40 Hz, Auto-Follow, GO, Pause, Master, Blackout) mit simulierter
  Uhr in wenigen Sekunden und prüft, dass der Heap nicht wächst.

## Betrieb & Zugriff

- Zielplattform ist ein **Laptop am Regieplatz**: Server, Browser und MIDI-Controller laufen auf demselben Gerät,
  der Art-Net-Node hängt per Netzwerkkabel dran.
- Der HTTP-Server lauscht standardmäßig nur auf **`127.0.0.1`**. Zugriff von Tablet/Handy im Netz wird unter
  Settings freigeschaltet (wirkt nach einem Neustart, dann lauscht er auf `0.0.0.0`) oder fest per
  `lumen.network-access=true`.
- **Konfigurations-PIN** (optional): Lesen und der Betrieb (Playback, Master, Blackout, Wechsel in den Betriebsmodus)
  gehen immer ohne PIN. Alle anderen schreibenden Endpunkte und `programmer.set` per WebSocket brauchen eine
  HTTP-Session, die mit der PIN entsperrt wurde. Der Start des Betriebsmodus sperrt alle Browser wieder, also fragt
  der Weg zurück in die Konfiguration nach der PIN. Die PIN (4–12 Ziffern) liegt als gesalzener PBKDF2-Hash in
  `app_setting`; nach 5 falschen Eingaben ist das Entsperren 30 s blockiert.
- Umsetzung ohne Spring Security: ein `HandlerInterceptor` im Web-Adapter prüft die Session, die Logik
  (`AccessService`) liegt in der Application-Schicht. Eine Sperr-„Epoche“ zählt hoch, wenn alle Browser gesperrt
  werden; eine Session ist entsperrt, solange ihre Epoche aktuell ist.
