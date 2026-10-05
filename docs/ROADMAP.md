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

## ✅ Iteration 2 – Projekte & Patch in SQLite
- Adapter `sqlite`: sqlite-jdbc, Flyway `V1__projects_and_patch.sql` (siehe [PROJECT-DATA.md](PROJECT-DATA.md)), JdbcClient
- Domain: `Project`, `Universe`, `PatchedFixture`, `Group` + Patch-Validierung
- REST: Projekte (CRUD, aktivieren), Universen, Fixtures, Gruppen, Patch-Issues unter `/api/projects/{id}/…`
- Tests: Repository-Tests gegen temporäre SQLite-Datei

**DoD:** Projekt per REST anlegen und mit Geräten patchen, Daten überleben einen Neustart, Überschneidungen werden gemeldet.

## ✅ Iteration 3 – DMX-Engine & Art-Net 💡
- Code-Sprache auf Englisch umgestellt (Doku bleibt Deutsch)
- Domain: `Attribute`, `FixtureValues`, `DmxFrame`, `DmxRenderer` (8/16 Bit, Defaults, virtueller Dimmer, invert, Grand Master)
- Render-Loop mit `OutputLayer`/`LayerMerger` (Grundlage für Playback und den späteren Party-Modus)
- `Programmer` als erster Layer (Live-Werte)
- Adapter `artnet`: ArtDmx bauen und senden, Sequence-Nummern, Unicast/Broadcast
- Test: byte-genauer Vergleich des ArtDmx-Pakets
- `PATCH /api/programmer/fixtures/{id}`, `GET /api/output/universes/{n}/dmx`
- Patch-Änderungen und Projektwechsel werden sofort in die Ausgabe übernommen

**DoD:** Per `curl` wird ein echter Scheinwerfer eingeschaltet (oder ist in QLC+/Wireshark sichtbar).
Ohne Hardware geprüft: Die echte App sendet ~40 ArtDmx-Pakete/s mit korrektem Header und den gesetzten Kanalwerten.

## ✅ Iteration 4 – UI-Grundgerüst & Konfigurationsmodus
- Modul `lumen-frontend` (Svelte + Vite + TS), Build via `frontend-maven-plugin` ins JAR
- Design Tokens, Dark Theme, App-Shell mit Moduswechsel (Betrieb vorerst Platzhalter)
- Projekt-Auswahl, Universen-Verwaltung, Patch-Ansicht mit Gerätebibliothek und Adress-Raster
- Zusätzlich: „Locate“-Knopf pro Gerät (leuchtet hell auf, um es auf der Bühne zu finden), Mehrfach-Patch mit
  Anzahl und automatischer nächster freier Adresse, Patch-Probleme anklickbar, Server-Status in der Kopfleiste
- Stage und Scenes sind als „Coming soon“ angelegt, der Show-Modus als Platzhalter

**DoD:** Ein komplettes Projekt lässt sich im Browser anlegen und patchen.

## ✅ Iteration 5 – Programmer & Live-Sync
- WebSocket `/ws`, `StateNotifier`, Throttling, Reconnect
- Dynamischer Programmer (Farbrad, Farbknöpfe, XY-Pad, Range-Fader, Rohkanäle)
- Gruppen, Mehrfachauswahl, Grand Master, Blackout
- Geräte-Karten mit Glow

- Umgesetzt als eigener Tab **Programmer**: Gruppen links, leuchtende Gerätekarten (Farbe/Helligkeit wie das echte
  Licht, inkl. Grand Master), dynamisches Panel rechts; Master-Leiste mit Blackout (Taste `B`) unten
- WebSocket: Server bündelt Änderungen (max. ~30/s), Client sendet Fader-Bewegungen gedrosselt, Fallback auf REST

**DoD:** Licht ist im Browser frei steuerbar, ein zweiter Browser zeigt alle Änderungen live.

## ✅ Iteration 6 – Szenen programmieren
- Domain: `Scene`, `AttributeValues`; Repository für Szenen
- Szenenliste: anlegen, Nummern wie „2.5“, Name, Regiehinweis, optionale Auto-Follow-Dauer; Reihenfolge ergibt sich aus
  der Nummer (statt Drag & Drop – sonst könnte Szene 5 vor Szene 3 stehen)
- „Record scene“ direkt im Programmer, Farbvorschau pro Szene in der Liste
- Nebenbei: Cache-Header für das Web-UI (sonst zeigt der Browser nach einem Update die alte Oberfläche)
- Aufnehmen / Aktualisieren aus dem Programmer, Szene in den Programmer laden (Vorschau)
- Einstellungen: **globale Fade-In-/Fade-Out-Zeit**

**DoD:** Eine Show mit 5 Szenen ist programmiert und nach einem Neustart vollständig vorhanden.

## ✅ Iteration 6b – Art-Net Discovery & Unicast 📡
Anlass: echte Hardware (eigener ESP8266-Dimmer, 1 Kanal, im WLAN). Broadcast schickt die Pakete **aller** Universen an
**alle** Geräte und ist im WLAN unzuverlässig (keine Bestätigung, langsamste Funkrate).
- Adapter `artnet`: **ArtPoll** periodisch senden (z.B. alle 3 s), **ArtPollReply** auswerten: IP, Name, gemeldete
  Port-Adressen; Nodes ohne Antwort nach ~10 s als offline markieren
- Neuer Ausgabe-Modus pro Universe: **Auto (Discovery)** – Pakete per Unicast an alle Nodes, die dieses Universe melden
- Mehrere **manuelle Ziel-IPs** pro Universe; Broadcast bleibt als Option
- Keine Migration nötig: `universe.target` speichert `auto`, `auto,<ips>`, `<ips>` oder `broadcast`; bestehende Werte bleiben gültig
- UI: Liste der gefundenen Nodes (Name, IP, Universen, online/offline), Zuordnung im Universe-Dialog
- Hinweis im UI, wenn ein Universe an keinen Node geht bzw. ein Node ein Universe meldet, das es im Projekt nicht gibt

**DoD:** Der ESP-Dimmer wird automatisch gefunden und bekommt seine Pakete per Unicast, ohne dass eine IP eingetragen ist.
Geprüft: Der echte ESP (192.168.0.150, Port-Adresse 0) wurde per ArtPoll gefunden; der Unicast an gefundene Nodes ist
mit einem simulierten ESP (gleiches ArtPollReply-Format wie die Firmware) end-to-end getestet.

## ✅ Iteration 7 – Playback-Engine & Betriebsmodus 🎭
- Domain: `Playback`, `Crossfade` mit Snapshot, HTP/LTP-Mischung, Pause, injizierte `Clock`
  (ausführliche Unit-Tests für das Fade-Verhalten, siehe [PLAYBACK.md](PLAYBACK.md))
- GO / Back / Springen / Pause / Fade abschließen, Start- und Endzustand (Blackout vor Szene 1, „Ende“ nach der letzten)
- Auto-Follow: automatisches GO nach der Stay-Dauer, Countdown im UI
- Betriebsmodus-UI: Szenenliste, Jetzt/Ausblendend/Nächste mit Fortschritt, Regiehinweis, großer GO-Button,
  Tastaturkürzel, Sperre gegen Bearbeiten, Wake Lock
- Show-Modus ist Server-Zustand: sperrt den Programmer (409), alle verbundenen Clients wechseln mit
- Noch offen (→ später): Probe-Überblendung im Konfigurationsmodus und Fade-Zeiten pro Szene → beide umgesetzt in
  Iteration 10

**DoD:** Eine komplette Show kann per Leertaste durchgespielt werden, Fade-Out und Fade-In laufen parallel,
ein Doppel-GO erzeugt keinen Sprung.

## ✅ Iteration 8 – Theatertauglichkeit / Robustheit 🛡️
- `playback_state` persistieren, Wiederaufnahme nach Neustart: Banner „Resume the show?“ im Betriebsmodus, die Szene
  kommt ohne Fade zurück. Auch der Betriebsmodus überlebt einen Neustart (`app_setting`), Flyway `V3`
- Projekt-Export/-Import als `.lumen.json` (Import legt immer ein neues Projekt an), automatische DB-Backups per
  `VACUUM INTO` beim Start, Projektwechsel und Start des Betriebsmodus, die neuesten 30 bleiben
- Render-Loop fängt auch `Error`s ab, Watchdog ersetzt einen hängenden Render-Thread; Health-Status (Render-Rate,
  Art-Net-Pakete/s, Sendefehler) jede Sekunde per WebSocket, Anzeige in der Kopfleiste
- Konfigurations-PIN (PBKDF2-Hash in `app_setting`, Sperre nach 5 Fehlversuchen), der Start des Betriebsmodus sperrt
  alle Browser. Netzwerkzugriff per Einstellung (wirkt nach Neustart). Umgesetzt ohne Spring Security
  (Session + `HandlerInterceptor`)
- Neuer Tab **Settings**: PIN, Netzwerkzugriff, Backups
- Durchlauftest `ShowSoakTest`: 3 h Show-Simulation (432.000 Frames, simulierte Uhr) in ca. 5 s, Heap bleibt konstant

**DoD:** Server während der Szene 4 hart beenden und neu starten: Licht ist nach Bestätigung wieder in Szene 4.
Geprüft mit `ResumeAfterRestartTest`: zwei App-Starts auf derselben Datenbank. Nach dem Neustart steht der Server
wieder im Betriebsmodus, das Licht ist dunkel, und nach „Resume“ liegt Szene 4 wieder auf dem DMX-Ausgang.

## ✅ Iteration 9 – Bühnenansicht & Komfort 🎪
- Tab **Stage**: Fixtures per Drag & Drop (Pointer Events, auch per Touch) auf die Bühne ziehen, aus der Bühne
  hinausziehen nimmt sie herunter, „Place all“ verteilt den Rest. Optionaler Grundriss (PNG/JPEG/WebP bis 5 MB,
  Tabelle `stage_plan`, Flyway `V4`, reist im Projekt-Export mit). Fixtures leuchten mit der **echten Ausgabe**
  (Programmer + Playback + Master); Lichtkegel als Lichtfleck, bei Moving Heads grob nach Pan/Tilt versetzt
- Live-Ausgabe per WebSocket-Abo (`output`, `dmx:<n>`), höchstens 10×/s und nur bei Änderungen
- Farbvorschau pro Szene jetzt auch in der Szenenliste des Betriebsmodus
- Hot Reload der Gerätebibliothek: Polling alle 2 s statt `WatchService` (der pollt auf macOS ohnehin nur etwa alle
  10 s), geladen wird erst, wenn die Dateien eine Runde lang unverändert sind; zusätzlich `POST /api/library/reload`
- Tab **DMX**: Monitor mit 512er-Raster pro Universe, Kanalzuordnung (Fixture + Kanalname), Prozent-Anzeige
- Responsive: Tablet hochkant zeigt im Betrieb Status und GO oben, die Liste darunter; auf dem Handy nur Jetzt/Nächste,
  GO und Back (kein Master/Blackout, kein Pause)

**DoD:** Auf dem Tablet ist die Show komfortabel einrichtbar und spielbar.
Geprüft im Browser (Desktop, Tablet-Hochformat 820 px, Handy 390 px): Bühne einrichten per Ziehen, Live-Glow bei
Szenenwechsel, DMX-Monitor, Show-Steuerung. Auf einem echten Tablet noch nicht ausprobiert.

## ✅ Iteration K – Kompositionen & dynamische Szenen 🌈
Konzept und Entscheidungen: [COMPOSITIONS.md](COMPOSITIONS.md). Szenen bestehen danach aus einer **statischen Basis**
(wie bisher) plus beliebig vielen **Kompositionen**: Abläufe wie Lauflicht, Farbwechsel auf RGB-PARs oder freie
Schrittfolgen, die in der Szene über dem festen Licht mitlaufen. Kompositionen werden in einer Bibliothek gepflegt
und beim Einfügen in eine Szene **kopiert**. Die Kopie ist dort frei anpassbar.

**✅ K1 – Engine & Szenen-Integration**
- Domain: `Composition` mit Vorlagen als reine Funktionen: **Lauflicht**, **Farbwechsel**, **Regenbogen**,
  **Welle/Puls**, **Flackern**; Ziel = Gruppe (dynamisch) oder Fixture-Liste in fester Reihenfolge, Spreizung
- **Schrittfolge (Chaser):** Schritte mit Werten pro Fixture, Halte-/Überblendzeit pro Schritt, Loop/Ping-Pong
- Mischregeln Basis ⊕ Kompositionen (Intensität absolut/HTP oder relativ, Farbe LTP mit Level, spätere gewinnt)
- `Playback`/`Crossfade` mit zeitabhängigen Zielwerten: Kompositionen laufen beim Fade-Out weiter, eine Kopie mit
  gleicher Herkunft läuft in der Folgeszene nahtlos weiter (pro Einsatz „Neu starten“), Pause friert sie ein
- Flyway-Migration `composition` (Bibliothek, Szenen-Kopien, Extra-Tasten), `composition_target`, `composition_step`,
  `composition_step_value`; REST für Bibliothek, Schritte und Kopien in Szenen
- Umgesetzt: alle fünf Vorlagen und Schrittfolgen als reine Funktionen mit Tabellen-Tests, `CompositionMixer`,
  Playback mit Weiterlaufen beim Fade-Out und nahtlosem Übergang bei gleicher Herkunft (`V5`). Kompositionen reisen im
  Projekt-Export mit. Der Durchlauftest spielt jetzt Szenen mit Lauflicht, Farbwechsel und durchlaufendem Flackern
  (ca. 50 µs pro Frame, Heap konstant). Noch ohne Oberfläche: bedienbar ist K1 bisher nur über die REST-API

**✅ K2 – Editor & Vorschau**
- Neuer Tab „Compositions“ (Bibliothek): Vorlage wählen, Ziel und Reihenfolge, Parameter, animierte Vorschau
- Schrittfolgen-Editor: Schritte aus dem Programmer aufnehmen/aktualisieren, Zeiten, Umsortieren
- Live-Vorschau auf der Bühne als eigene Ausgabe-Ebene (im Show-Modus gesperrt)
- Szenen-Editor: aus der Bibliothek einfügen (Kopie), anpassen, Reihenfolge, Level, Tempo-Faktor, „Neu starten“,
  „aus Bibliothek neu laden“ / „in Bibliothek übernehmen“; Hinweise bei Überschneidungen und unpassenden Geräten
- Szenenliste: Symbol und animierte Farbvorschau für dynamische Szenen
- Umgesetzt: Tab „Compositions“ mit Editor für alle Typen (speichert automatisch). Die Vorschau im Editor rechnet der
  Server und schickt sie per WebSocket (`preview:<id>`), die Bühne bleibt dabei unberührt; „Show on stage“ legt die
  Komposition als eigene Ebene (Priorität 75) über das Playback. Szenen-Editor mit Abschnitt „Compositions“,
  Hinweise auf Überschneidungen, unpassende Geräte und schnelles Blitzen (⚡, ab ca. 3 Hz). Abweichung: Die
  Szenenlisten zeigen Typ-Symbole statt einer animierten Farbvorschau

**✅ K3 – Extra-Tasten & Erweiterungen**
- **Extra-Tasten im Betriebsmodus:** Kompositionen unabhängig von der Szene zuschalten (Toggle/Flash, Tasten `1`–`8`,
  später MIDI), eigene Ausgabe-Ebene über dem Playback
- **Einmalige Abläufe** (laufen einmal durch und bleiben am Ende stehen, z.B. Sonnenaufgang)
- Multi-Pixel-Zellen als Ziel, Kennzeichnung schneller Flacker-/Strobe-Effekte
- Umgesetzt: 8 Extra-Tasten pro Projekt (Toggle/Flash, Fade-Zeit, eigene Kopie der Komposition), **pro Szene
  freigeschaltet** (neue Taste: in allen Szenen, neue Szene: erbt die Tasten der Szene davor), gehen bei einer Szene
  ohne die Taste und beim „Ende“ aus (Flyway `V6`). Tastenleiste und Tasten `1`–`8` im Betriebsmodus, ohne PIN.
  „Run once“ für alle Typen mit Ende (nicht Flackern / zufälliges Lauflicht). Kennzeichnung schneller Effekte (⚡)
  seit K2. **Verschoben:** Multi-Pixel-Zellen – die Gerätebibliothek kennt noch keine Multi-Pixel-Geräte

**DoD:** Eine Szene mit warmweißem Grundlicht, einem Lauflicht auf einer Dimmer-Reihe und einem Farbwechsel auf
den RGB-PARs lässt sich im Browser zusammenstellen. Im Betrieb blenden die Abläufe per GO weich ein und aus, laufen
in der Folgeszene ohne Sprung weiter, und Pause hält sie an. Per Extra-Taste lässt sich ein Gewitter über jede Szene
legen.
Geprüft: Zusammenstellen und Vorschau im Browser (Testserver), Weiterlaufen/Pause/Doppel-GO in Domain-Tests, im
Betrieb ein „Thunderstorm“ auf Taste 1 per Tastatur über die warmweiße Szene gelegt und wieder ausgeschaltet. Noch
nicht mit echten Geräten ausprobiert.

## ⬜ Iteration M – MIDI-Controller 🎛️ (optional, ab Iteration 7 jederzeit möglich)
Konzept: [MIDI.md](MIDI.md)
- Modul `lumen-adapter-midi` mit `javax.sound.midi`: Geräteerkennung, Hotplug, Eingangs-Events
- Controller-Profile als YAML (`midi-controllers/`), erstes Profil für den vorhandenen Controller
- Tabelle `midi_mapping` pro Projekt (Flyway-Migration), Aktionen für Betrieb und Konfiguration
- MIDI-Learn und MIDI-Monitor im UI, Soft-Takeover für Fader
- LED-Feedback über `MidiFeedbackPort`

**DoD:** GO, Back, Grand Master und Blackout funktionieren über den Controller und die Button-LEDs zeigen den Zustand.
Im Konfigurationsmodus steuern Fader Gruppen-Dimmer.

## 🟨 Iteration 10 – Backlog (nach Bedarf)
- ✅ **Fade-Zeiten pro Szene** (In, Out, Delay), siehe [PLAYBACK.md](PLAYBACK.md#fade-zeiten-pro-szene): Die Zeiten
  gelten für das GO in die Szene, leer = Projektzeit. Flyway `V8`, reist im Projekt-Export mit. Szenen-Editor mit
  Feldern und ⏱-Badge in beiden Szenenlisten, der Betriebsmodus zeigt „Fade in starts in …“ während des Delays.
  Geprüft mit Domain-Tests und im Browser (Testserver: Blitz schaltet sofort, Delay hält den Fade-In zurück)
- ✅ **Probe-Überblendung** im Konfigurationsmodus: „⇢ Rehearse fade“ im Szenen-Editor spielt das GO von der Szene
  davor (bzw. von dunkel) in die gewählte Szene, als Bühnen-Vorschau (Ebene 75) mit derselben `Playback`-Engine wie
  im Betrieb, also mit Fade-Zeiten, Delay und Kompositionen. `POST /api/projects/{id}/scenes/{sid}/rehearse-fade`,
  der Status der Bühnen-Vorschau enthält den laufenden Fade. Geprüft im Browser (Testserver)
- ✅ Nebenbei: Robustheit – Playback liest beim Reload die Datenbank außerhalb der Sperre (Render-Loop wartet nicht),
  eine während der Show gelöschte Szene bleibt bis zum nächsten GO stehen, nach dem Ende kommt keine neu angelegte
  Szene auf die Bühne. WebSocket-Watchdog (3,5 s ohne Nachricht → neu verbinden), Wake Lock mit NoSleep.js-Fallback
  für Tablets über HTTP, `caffeinate` in `run.sh`, ruhigere Test-Ausgabe
- Externe GO-Taste: USB-Fußschalter (als Tastatur), OSC (z.B. aus QLab); MIDI siehe eigene Iteration
- sACN als zweiter `DmxOutputPort`
- Hostnamen (mDNS, z.B. `artnet-dimmer.local`) als Art-Net-Ziel – noch offen, ob gewünscht
- Blitz-Effekt als Szenen-Attribut (Kerzen-/Feuerflackern ist jetzt Teil von Iteration K)
- PWA, Docker / Raspberry Pi als alternativer Betriebsort
- Import von Open-Fixture-Library-Profilen
- ✅ **Druckbare Cue-Liste** für die Regie: „🖨 Cue list“ in der Szenenliste öffnet eine Druckseite (`#/print/cues`,
  hell, A4, Kopfzeile wiederholt sich auf jeder Seite): Nummer, Szene, „GO on“ (Regiehinweis), In/Out/Delay (eigene
  Zeiten fett), Auto-Follow, laufende Kompositionen, freigeschaltete Extra-Tasten, Abhak-Kästchen; darunter die
  Extra-Tasten. PDF über „Als PDF sichern“ im Druckdialog statt einer PDF-Bibliothek im Server

## ⬜ Iteration 11+ – Party-Modus 🪩 (Zukunft)
Konzept: [PARTY-MODE.md](PARTY-MODE.md). Grob in Teilschritten:
1. **Tempo & Paletten:** Tap-Tempo/BPM-Clock, Farb- und Positionspaletten, Party-UI mit Gruppen + Paletten
2. **Effekt-Engine:** nutzt die Kompositionen aus Iteration K, ergänzt BPM-Kopplung (Bewegungsbilder separat, siehe unten)
3. **Executors:** frei belegbare Slots mit Fader, Flash und Toggle (Weiterentwicklung der Extra-Tasten und Chaser aus Iteration K)
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
- Weitere Geräte außer dem eigenen ESP8266-Dimmer (Profil `fixture-types/diy/`)? Bis dahin generische Beispielprofile
  (RGBW-PAR, Dimmer, Moving Head).
- Welcher MIDI-Controller? Bis dahin generisches MIDI.
