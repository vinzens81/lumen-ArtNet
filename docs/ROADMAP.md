# Roadmap – iterative Teilschritte

Jede Iteration endet mit einem **lauffähigen Stand** und einer klaren **Definition of Done (DoD)**.
Reihenfolge so gewählt, dass möglichst früh echtes Licht angeht und eine Show so früh wie möglich spielbar ist.

Legende: ⬜ offen · 🟨 in Arbeit · ✅ fertig

---

## ✅ Iteration 0 – Projektgerüst
- Maven Multi-Module (siehe [ARCHITECTURE.md](ARCHITECTURE.md)), Java 26, Spring Boot 4.1.1 (via Spring Initializr)
- Angelegt: `lumen-domain`, `lumen-application`, `lumen-app`; Adapter-Module folgen in ihren Iterationen
- `lumen-app` startet, `/actuator/health` antwortet
- ArchUnit-Test: Domain/Application hängen nicht von Spring oder Adaptern ab
- `.gitignore`, `git init`, `CLAUDE.md` mit Build-Befehlen

**DoD:** `mvn verify` grün, App startet.

## ✅ Iteration 1 – Gerätebibliothek (YAML)
- Domain: `FixtureType`, `Mode`, `ChannelDefinition`, `ChannelFunction`, `FixtureTypeValidator`
- Adapter `library-yaml`: lädt `fixture-types/` (inkl. Unterordner), unbekannte Felder werden als Fehler gemeldet
- Adapter `web`: erster REST-Controller
- Beispielprofile im Repo (PAR, Dimmer, Moving Head)
- `GET /api/library/fixture-types`, `GET /api/library/fixture-types/{id}`, `GET /api/library/issues`

**DoD:** Beispielprofile werden geladen, eine fehlerhafte YAML erzeugt verständliche Issues, ohne den Start zu verhindern.

## ⬜ Iteration 2 – Projekte & Patch in SQLite
- Adapter `sqlite`: sqlite-jdbc, Flyway `V1__init.sql` (siehe [PROJECT-DATA.md](PROJECT-DATA.md)), JdbcClient
- Domain: `Project`, `Universe`, `PatchedFixture`, `Group` + Patch-Validierung
- REST: Projekte (CRUD, aktivieren), Universen, Fixtures, Gruppen, Patch-Issues
- Tests: Repository-Tests gegen temporäre SQLite-Datei

**DoD:** Projekt per REST anlegen und mit Geräten patchen, Daten überleben einen Neustart, Überschneidungen werden gemeldet.

## ⬜ Iteration 3 – DMX-Engine & Art-Net 💡
- Domain: `OutputState`, `DmxFrame`, Mapping Attribute → DMX (8/16 Bit, Defaults, virtueller Dimmer, invert)
- Render-Loop mit `OutputLayer`/`LayerMerger` (Grundlage für Playback und den späteren Party-Modus)
- `Programmer` als erster Layer (Live-Werte)
- Adapter `artnet`: ArtDmx bauen und senden, Sequence-Nummern, Unicast/Broadcast
- Test: byte-genauer Vergleich des ArtDmx-Pakets
- `PATCH /api/programmer/fixtures/{id}`, `GET /api/output/universes/{n}/dmx`

**DoD:** Per `curl` wird ein echter Scheinwerfer eingeschaltet (oder ist in QLC+/Wireshark sichtbar).

## ⬜ Iteration 4 – UI-Grundgerüst & Konfigurationsmodus
- Modul `lumen-frontend` (Svelte + Vite + TS), Build via `frontend-maven-plugin` ins JAR
- Design Tokens, Dark Theme, App-Shell mit Moduswechsel (Betrieb vorerst Platzhalter)
- Projekt-Auswahl, Universen-Verwaltung, Patch-Ansicht mit Gerätebibliothek und Adress-Raster

**DoD:** Ein komplettes Projekt lässt sich im Browser anlegen und patchen.

## ⬜ Iteration 5 – Programmer & Live-Sync
- WebSocket `/ws`, `StateNotifier`, Throttling, Reconnect
- Dynamischer Programmer (Farbrad, Farbknöpfe, XY-Pad, Range-Fader, Rohkanäle)
- Gruppen, Mehrfachauswahl, Grand Master, Blackout
- Geräte-Karten mit Glow

**DoD:** Licht ist im Browser frei steuerbar, ein zweiter Browser zeigt alle Änderungen live.

## ⬜ Iteration 6 – Szenen programmieren
- Domain: `Scene`, `AttributeValues`; Repository für Szenen
- Szenenliste: anlegen, Nummern wie „2.5“, Name, Regiehinweis, optionale Auto-Follow-Dauer, Reihenfolge per Drag & Drop
- Aufnehmen / Aktualisieren aus dem Programmer, Szene in den Programmer laden (Vorschau)
- Einstellungen: **globale Fade-In-/Fade-Out-Zeit**

**DoD:** Eine Show mit 5 Szenen ist programmiert und nach einem Neustart vollständig vorhanden.

## ⬜ Iteration 7 – Playback-Engine & Betriebsmodus 🎭
- Domain: `Playback`, `Crossfade` mit Snapshot, HTP/LTP-Mischung, Pause, injizierte `Clock`
  (ausführliche Unit-Tests für das Fade-Verhalten, siehe [PLAYBACK.md](PLAYBACK.md))
- GO / Back / Springen / Pause / Fade abschließen, Start- und Endzustand (Blackout vor Szene 1, „Ende“ nach der letzten)
- Auto-Follow: automatisches GO nach der Stay-Dauer, Countdown im UI
- Betriebsmodus-UI: Szenenliste, Jetzt/Ausblendend/Nächste mit Fortschritt, Regiehinweis, großer GO-Button,
  Tastaturkürzel, Sperre gegen Bearbeiten, Wake Lock
- Probe-Überblendung im Konfigurationsmodus

**DoD:** Eine komplette Show kann per Leertaste durchgespielt werden, Fade-Out und Fade-In laufen parallel,
ein Doppel-GO erzeugt keinen Sprung.

## ⬜ Iteration 8 – Theatertauglichkeit / Robustheit
- `playback_state` persistieren, Wiederaufnahme nach Neustart
- Projekt-Export/-Import, automatische DB-Backups
- Render-Loop gegen Fehler absichern, Watchdog/Health-Status im UI (Art-Net live, Pakete/s)
- Konfigurations-PIN (gehasht in `app_setting`), Netzwerkzugriff per Einstellung freischalten
- Durchlauftest: 3 h Show-Simulation ohne Speicherwachstum

**DoD:** Server während der Szene 4 hart beenden und neu starten: Licht ist nach Bestätigung wieder in Szene 4.

## ⬜ Iteration 9 – Bühnenansicht & Komfort
- 2D-Bühne mit Positionen, Drag & Drop, optionales Grundriss-Bild, Glow und Lichtkegel
- Farbvorschau pro Szene in der Liste
- Hot Reload der Gerätebibliothek (`WatchService`)
- DMX-Monitor (512er-Raster)
- Responsive: Tablet als Pult, Handy als Fernbedienung (nur GO/Back im Betrieb)

**DoD:** Auf dem Tablet ist die Show komfortabel einrichtbar und spielbar.

## ⬜ Iteration M – MIDI-Controller 🎛️ (optional, ab Iteration 7 jederzeit möglich)
Konzept: [MIDI.md](MIDI.md)
- Modul `lumen-adapter-midi` mit `javax.sound.midi`: Geräteerkennung, Hotplug, Eingangs-Events
- Controller-Profile als YAML (`midi-controllers/`), erstes Profil für den vorhandenen Controller
- Tabelle `midi_mapping` pro Projekt (Flyway-Migration), Aktionen für Betrieb und Konfiguration
- MIDI-Learn und MIDI-Monitor im UI, Soft-Takeover für Fader
- LED-Feedback über `MidiFeedbackPort`

**DoD:** GO, Back, Grand Master und Blackout funktionieren über den Controller und die Button-LEDs zeigen den Zustand.
Im Konfigurationsmodus steuern Fader Gruppen-Dimmer.

## ⬜ Iteration 10 – Backlog (nach Bedarf)
- Fade-Zeiten pro Szene überschreiben, Delay
- Externe GO-Taste: USB-Fußschalter (als Tastatur), OSC (z.B. aus QLab); MIDI siehe eigene Iteration
- Art-Net Discovery (ArtPoll), sACN als zweiter `DmxOutputPort`
- Einfache Theater-Effekte als Szenen-Attribut (Kerzen-/Feuerflackern, Blitz) – nutzt die Effekt-Engine aus Iteration 11
- PWA, Docker / Raspberry Pi als alternativer Betriebsort
- Import von Open-Fixture-Library-Profilen
- Druckbare Cue-Liste (PDF) für die Regie

## ⬜ Iteration 11+ – Party-Modus 🪩 (Zukunft)
Konzept: [PARTY-MODE.md](PARTY-MODE.md). Grob in Teilschritten:
1. **Tempo & Paletten:** Tap-Tempo/BPM-Clock, Farb- und Positionspaletten, Party-UI mit Gruppen + Paletten
2. **Effekt-Engine:** Intensitäts- und Farbeffekte mit Phasenversatz, BPM-gekoppelt (Bewegungsbilder separat, siehe unten)
3. **Executors:** frei belegbare Slots mit Fader, Flash und Toggle; Chaser
4. **Live-Werkzeuge:** Flash All, Strobe All, Fog, Speed-Master, Look als Szene speichern
5. **Extras:** Beat-Erkennung per Mikrofon, MIDI-Mappings für Executors (setzt die MIDI-Iteration voraus)

**DoD (Schritt 1–3):** Auf einer Party lassen sich per Tablet Farben im Takt wechseln, und Effekte sind über
Executor-Fader dosierbar.

## ⬜ Iteration 12+ – Bewegungsbilder für Moving Heads & Scanner 🌀 (Zukunft)
Konzept und offene Fragen: [MOVEMENT.md](MOVEMENT.md). Noch nicht ausgearbeitet, wird vor dem Start gemeinsam
konkretisiert.
- Formen (Kreis, Acht, Linie, Welle, …) mit Pan/Tilt-Offset, Scale (getrennt Pan/Tilt), Rotation, Speed, Phasenversatz
- Montage-Infos und Pan/Tilt-Limits pro Fixture im Patch
- Editor mit animierter Vorschau
- Nutzung im Party-Modus und ggf. als Szenen-Attribut im Theater-Modus

Bis dahin: nur statische Pan/Tilt-Positionen (XY-Pad).

---

## Arbeitsweise
- Pro Iteration ein Branch, kleine Commits, Domain-Logik testgetrieben.
- Nach jeder Iteration: Status hier aktualisieren, Erkenntnisse notieren.

## Entscheidungen (2026-10-02)
| Thema | Entscheidung |
|---|---|
| Stay | bis zum GO, optional **Auto-Follow** pro Szene (Iteration 6/7) |
| Betriebsort | **Laptop am Regieplatz**: Server, Browser und MIDI-Controller auf einem Gerät; Pi/Docker nur Backlog |
| Java | **Java 26** (bereits installiert). Support endet März 2027, danach Upgrade auf das nächste Release einplanen |
| Frontend | **Svelte 5** + TypeScript + Vite |
| Zugriff | **PIN für den Konfigurationsmodus**, Betrieb ohne Login; Server lauscht standardmäßig nur auf `localhost` |
| Name | **Lumen**, Basis-Package `de.vinzens.lumen` |

## Offene Fragen
- Welche Geräte und welcher Art-Net-Node sind konkret vorhanden? Bis dahin generische Beispielprofile
  (RGBW-PAR, Dimmer, Moving Head).
- Welcher MIDI-Controller? Bis dahin generisches MIDI.
