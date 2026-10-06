# Roadmap – iterative Teilschritte

Jede Iteration endet mit einem **lauffähigen Stand** und einer klaren **Definition of Done (DoD)**.
Reihenfolge so gewählt, dass möglichst früh echtes Licht angeht und eine Show so früh wie möglich spielbar ist.

Legende: ⬜ offen · 🟨 in Arbeit · ✅ fertig · 🧪 experimentell

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
  (→ umgesetzt in Iteration G: „Each head separately“)

**DoD:** Eine Szene mit warmweißem Grundlicht, einem Lauflicht auf einer Dimmer-Reihe und einem Farbwechsel auf
den RGB-PARs lässt sich im Browser zusammenstellen. Im Betrieb blenden die Abläufe per GO weich ein und aus, laufen
in der Folgeszene ohne Sprung weiter, und Pause hält sie an. Per Extra-Taste lässt sich ein Gewitter über jede Szene
legen.
Geprüft: Zusammenstellen und Vorschau im Browser (Testserver), Weiterlaufen/Pause/Doppel-GO in Domain-Tests, im
Betrieb ein „Thunderstorm“ auf Taste 1 per Tastatur über die warmweiße Szene gelegt und wieder ausgeschaltet. Noch
nicht mit echten Geräten ausprobiert.

## ✅ Iteration T – Textbuch im Betriebsmodus 📜
Anlass: Im Betrieb soll man nur einer Ansicht folgen müssen, nicht Textbuch und Lichtpult getrennt.
- Jede Szene hat ihren **eigenen Text** (`scene.script`, Flyway `V10`, reist im Projekt-Export mit), eingegeben im
  Szenen-Editor. Kein durchlaufender Text über alle Szenen
- Zerlegung in Sätze an `.`, `?`, `!` und `…`, aber nicht in Zahlen („3.5“) und Abkürzungen („z.B.“, „Dr.“, „U.S.A.“);
  `**Notiz**` ist eine Regieanweisung: anders dargestellt und von den Pfeiltasten übersprungen
- Betriebsmodus: neuer Reiter **Script** links neben „Scenes“ und „DMX“, zeigt den Text der Szene auf der Bühne. GO
  bleibt fest rechts an seinem Platz
- `↑`/`↓` gehen zum vorigen/nächsten Satz, der aktuelle Satz ist hervorgehoben und wird automatisch mittig gehalten.
  Die Wahl der nächsten Szene wandert auf `Shift+↑`/`Shift+↓`
- Die Textposition ist **Server-Zustand** (Teil des Playback-Status): Laptop und Tablet zeigen denselben Satz. Bei einem
  Szenenwechsel beginnt sie wieder beim ersten Satz

**DoD:** Eine Szene mit mehreren Absätzen und Notizen lässt sich im Betrieb Satz für Satz per Pfeiltaste durchgehen,
ein zweiter Browser folgt mit, Notizen werden übersprungen, der aktuelle Satz bleibt auch bei langem Text im Blick.
Umgesetzt: Zerlegung als reine Logik (`src/lib/script.ts`, Vitest); ein Zeilenumbruch beendet ebenfalls einen Satz,
nach einem Satzzeichen mit klein geschriebenem nächsten Wort („Ich… ich“) wird nicht geteilt. Textposition
`scriptLine` im Playback-Status, gesetzt per `PUT /api/playback/script` bzw. WebSocket `playback.script` (ohne PIN),
nicht persistiert: nach einem Neustart beginnt die Szene beim ersten Satz. Auf dem Tablet ▲/▼-Knöpfe statt Pfeiltasten,
auf dem Handy ist das Textbuch ausgeblendet. Geprüft im Browser (Testserver): 24 Sätze mit Regieanweisungen,
automatisches Mitscrollen bis zum Ende, GO startet die nächste Szene beim ersten Satz, eine per API gesetzte Position
erscheint sofort in der Oberfläche.
- Nachtrag (2026-10-06): **Sprecher** – ein Name mit Doppelpunkt am Zeilenanfang („Maike Schmitz: Hallo Rolf!“, 1–4
  groß geschriebene Wörter) wird farbig markiert (jede Person immer dieselbe Farbe), ihr Text steht eingerückt darunter,
  bis die nächste Person spricht oder eine Leerzeile kommt. Regieanweisungen stehen immer in einer eigenen Zeile, ohne
  Einrückung; spricht danach dieselbe Person weiter, geht ihr Text eingerückt weiter. Der Szenen-Editor zählt die Sprecher

## ✅ Iteration N – Netzwerk-Interface pro Universe 🌐
Heute gibt es nur eine globale `bind-address`/`broadcast-address`, die Discovery hört auf `0.0.0.0`.
- Interfaces des Rechners auflisten (Name wie `en0`, IPv4, Broadcast), `GET /api/network/interfaces`
- Discovery schickt ArtPoll an die Broadcast-Adresse **jedes** aktiven Interfaces und ordnet gefundene Nodes ihrem
  Interface zu (Subnetz der Absender-IP, reine Logik mit Unit-Tests)
- Pro Universe ein Interface (Flyway, leer = alle/automatisch wie bisher): gilt für Broadcast und Auto-Discovery,
  gesendet wird über einen an das Interface gebundenen Socket
- UI: Auswahl im Universe-Dialog, Interface in der Node-Liste, Hinweis wenn das gewählte Interface fehlt

**DoD:** Mit WLAN und einem USB-Ethernet-Adapter gleichzeitig gehen die Pakete eines Universe nur ins gewählte Netz.
Umgesetzt, mit einer Abweichung: kein eigener Socket pro Interface. Ein Universe mit Interface sendet Broadcast an
den **gerichteten Broadcast** dieses Netzes (z.B. `2.255.255.255`) und im Modus Auto nur an Nodes in dessen Subnetz;
das Betriebssystem schickt beides von selbst über das passende Interface. Bisher ging Broadcast an
`255.255.255.255` und damit unter macOS nur über das Haupt-Interface. Manuelle IPs bleiben unverändert. Fehlt das
Interface (Adapter abgezogen), wird das Universe nicht gesendet statt in ein falsches Netz; die Universe-Ansicht warnt.
Interfaces werden höchstens alle 3 s neu gelesen (ca. 0,5 ms), `GET /api/artnet/networks`, Flyway `V11`, reist im
Projekt-Export mit. Geprüft mit Unit-Tests (Subnetz, Broadcast, Zielauswahl, ArtPoll an alle Netze) und im Browser
(Testserver); mit zwei echten Netzen und Art-Net an noch nicht ausprobiert.

## ✅ Iteration H – Node-Überwachung im Betrieb 🩺 (baut auf Iteration N auf)
- **Erwartete Nodes pro Projekt speichern** (Flyway), damit auch ein Node auffällt, der schon vor dem Start fehlt
- Node-Status pro Universe im Gesundheitsstatus: online / offline / manuelle IP ohne ArtPollReply (nicht prüfbar)
- Kopfleiste: „Nodes 3/3“ neben der Art-Net-Anzeige, gelb/rot sobald ein erwarteter Node fehlt, Details per Klick;
  Hinweis im Betriebsmodus, wenn während der Show ein Node wegfällt

**DoD:** Den ESP-Dimmer während einer Show vom Strom nehmen: nach spätestens ~10 s zeigt die Kopfleiste ihn als fehlend.
Umgesetzt: Ein Node wird erwartet, sobald er per ArtPollReply ein Universe des aktiven Projekts meldet (Flyway `V12`,
gespeichert, damit er auch nach einem Neustart fehlt); dazu die manuellen IPs der Universen, die ohne ArtPollReply
„nicht prüfbar“ bleiben. `NodeWatchService` prüft jede Sekunde mit dem Gesundheitsstatus (nur bei Art-Net an),
fehlende Nodes sind Warnungen („Node … does not answer – universe 1 may stay dark“). Kopfleiste „Nodes 1/1“, rot bei
fehlendem Node, Klick zeigt die Liste; „Forget“ (`DELETE /api/artnet/expected-nodes/{ip}`) nur im
Konfigurationsmodus. Im Betrieb zusätzlich ein Hinweis, wenn ein Node wegfällt. Nebenbei: Hinweise erscheinen in
Betriebs- und Party-Modus oben statt unten, damit sie GO, Back und die Tasten nicht verdecken.
Geprüft mit Unit-Tests und im Browser mit einem simulierten Node auf 127.0.0.1 (Lumen nur an Loopback gebunden):
nach dem Abschalten nach ca. 9 s „Nodes 0/1“ rot und Hinweis im Betrieb, nach dem Einschalten nach ca. 3 s wieder grün.
Mit dem echten ESP noch nicht ausprobiert.

## ✅ Iteration D – Mac-App 🍎
- `jpackage` (im JDK) baut `Lumen.app` mit eingebauter Java-Laufzeit und eine `.dmg` zum Installieren: in „Programme“
  ziehen, danach in Launchpad/Spotlight und im Dock wie jede andere App
- Eigenes kleines Fenster (Swing, eigenes Modul z.B. `lumen-desktop`): Status, „Open in browser“, „Quit“
- Daten unter `~/Library/Application Support/Lumen/` (DB, Backups, eigene Profile), mitgelieferte Profile in der App;
  Ruhezustand verhindern wie in `run.sh`
- Bekannt: ohne Apple-Developer-ID unsigniert (erster Start per Rechtsklick → Öffnen), Firewall-Abfrage für UDP 6454,
  Build nur für die eigene Architektur (Apple Silicon)

**DoD:** Lumen lässt sich aus der `.dmg` installieren, aus dem Dock starten und öffnet per Knopf die Oberfläche.
Umgesetzt mit `./package-mac.sh` → `dist/Lumen-<Version>.dmg` (Version `1.0.<Anzahl Commits>` oder `--version`,
`--fast` ohne Tests, `--no-build` verpackt das vorhandene JAR), ca. 96 MB mit eigener Java-Laufzeit, ad-hoc signiert.
Abweichung: kein eigenes Modul `lumen-desktop` – die App startet dasselbe JAR mit `-Dlumen.desktop=true`
(`DesktopLauncher` in `lumen-app`), ohne die Option läuft Lumen wie bisher ohne Fenster. Fenster mit Status,
„Open in browser“, „Open data folder“, „Show log“, „Quit“; Schließen versteckt es nur (Dock-Symbol holt es zurück),
Beenden (Quit, ⌘Q) fragt nach. Mitgelieferte Profile werden nur kopiert, wenn sie in der Bibliothek fehlen – eigene
Änderungen bleiben, neue Versionen mitgelieferter Profile kommen dadurch aber nicht automatisch an. Icon wird beim
Paketieren aus `packaging/macos/MakeIcon.java` gezeichnet.
Geprüft: DMG gebaut, App aus `/Applications` gestartet, Server nach 1,5 s bereit, Profile und Log am richtigen Ort.

## ✅ Iteration P – Programmier-Komfort ✍️
Anlass: Review vom 2026-10-05 – im Probenalltag fehlten Kopieren, Rückgängig und wiederverwendbare Farben/Positionen.
- **Szene duplizieren:** „⧉ Duplicate“ kopiert eine Szene direkt hinter sich (Nummer dazwischen) mit Bild, Zeiten,
  Text, Kompositionen (gleiche Herkunft, laufen also nahtlos weiter) und freigeschalteten Extra-Tasten
- **Teil-Update („Merge“):** „Update from programmer“ ersetzt das ganze Bild oder übernimmt nur, was der Programmer
  setzt – optional nur bestimmte Attributgruppen (Intensity, Color, Position, Beam, Other) und/oder nur die im
  Programmer ausgewählten Geräte (`AttributeGroup`, Auswahl jetzt geteilt zwischen Programmer und Szenen-Editor)
- **Szenen-Historie / Rückgängig:** Vor jedem Update, Merge und Restore wird das alte Bild als Revision gespeichert
  (die neuesten 20 je Szene, Flyway `V14`); „History“ im Szenen-Editor mit Farbvorschau und „Restore“, „↶ Undo“ für das
  letzte. Ein Restore ist selbst wieder rückgängig zu machen
- **Presets mit Verweis** (Farbe, Position, Beam; in der Oberfläche „Presets“, weil der Party-Modus schon „Paletten“
  hat): im Programmer aus der Auswahl aufnehmen und anwenden. Szenen und Programmer merken sich pro Gerät und
  Attributgruppe das Preset (`PresetRefs`, Flyway `V15`); ein Update des Presets schreibt die neuen Werte in alle
  Szenen, die es nutzen. Ein Wert von Hand löst das Gerät vom Preset; gelöschte Presets lassen die Werte stehen.
  Reisen im Projekt-Export mit
- Abweichung: Spec-Änderungen (Name, Zeiten, Text) und gelöschte Szenen sind nicht in der Historie – dafür gibt es
  weiterhin die DB-Backups

**DoD:** Szene duplizieren, nur die Farben aus dem Programmer übernehmen, versehentliches Update per Undo zurückholen;
ein Preset „Warm“ ändern und alle Szenen mit „Warm“ ändern sich mit.
Geprüft mit Domain-, Repository- und API-Tests (u.a. `PresetApiTest`: Update des Presets landet in der Szene, Export/
Import erhält die Verweise). Im Browser nur angesehen (Preset-Leiste und Dialog im Programmer), nicht durchgeklickt –
eine andere Chrome-Erweiterung blockierte die Automatisierung. Die Oberfläche ist mit `svelte-check`/Vitest geprüft.

## ✅ Iteration F – Fader & Fade-Kurven 🎚️
- **Fade-Kurven** pro Projekt und optional pro Szene: Linear, S-Kurve (auch für Farbe/Position), quadratisch („Square
  law“, weicher im Dunkeln), siehe [PLAYBACK.md](PLAYBACK.md#fade-kurven). Flyway `V13`, reist im Export mit, Auswahl in
  Projekt-Dialog, globalen Fade-Zeiten und Szenen-Editor; die Druckansicht nennt die Projektkurve
- **Fader im Betrieb** (z.B. Saallicht): bis zu 8 pro Projekt, je mit einer Szene belegt (Flyway `V16`), Level per
  WebSocket `fader.set` bzw. `PUT /api/playback/faders/{slot}` ohne PIN, im Konfigurations- und Betriebsmodus.
  Mischung wie Extra-Tasten mit Szene (HTP), über Playback und Extra-Tasten; Level bleibt beim Bearbeiten erhalten,
  nicht über einen Neustart. Siehe [PLAYBACK.md](PLAYBACK.md#fader-im-betrieb-zb-saallicht)

**DoD:** Saallicht per Fader im Betrieb stufenlos hochziehen, ohne dass GO es beeinflusst; eine Szene mit S-Kurve
blendet sichtbar weicher ein.
Geprüft mit Domain-Tests (Kurven, Mischung), `FaderApiTest` (DMX-Wert bei 50 %, Export/Import) und am laufenden
Testserver per API.

## ✅ Iteration G – Geräte 💡
- **Multi-Pixel-Geräte:** `heads`-Block (oder `head` pro Kanal) in der YAML, Attribute pro Kopf (`red@3`) mit
  Rückfall auf das ganze Gerät, virtueller Dimmer pro Kopf, Ausgabe im `DmxRenderer`; Programmer mit Kopf-Auswahl und
  Kopf-Leiste auf der Gerätekarte; Kompositionen mit „Each head separately“ (Flyway `V17`). Beispielprofil
  `generic/generic-led-bar-8-pixel.yml`. Hinweis: Die vorhandene Eurolite LED BAR-6 QCL kann ihre LEDs laut Handbuch nur
  gemeinsam steuern und bleibt deshalb ein Gerät ohne Köpfe
- **Import aus der Open Fixture Library:** OFL-JSON hochladen, Lumen schreibt ein YAML-Profil in die Bibliothek
  (`OflConverter`, `FixtureTypeYamlWriter`), Fine-Kanäle → 16 Bit, Matrix → Köpfe, Vereinfachungen als Warnungen.
  Siehe [FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md#import-aus-der-open-fixture-library)
- **Profil-Updates in der Mac-App:** Prüfsummen der installierten Version in `.bundled-profiles.properties`;
  unveränderte Profile werden ersetzt, bearbeitete bleiben (neue Version daneben als `.shipped`), gelöschte kommen
  nicht wieder

**DoD:** Ein Lauflicht läuft über die 8 Segmente einer Pixel-Bar; ein Moving Head aus der OFL ist nach dem Import
ohne Bibliotheks-Probleme patchbar; ein App-Update bringt korrigierte Profile mit, ohne eigene Änderungen zu überschreiben.
Geprüft mit Tests (Rundweg OFL → YAML → Bibliothek, Renderer, Lauflicht über Köpfe, Profil-Updates) und am laufenden
Testserver (Pixel-Bar auf dem DMX-Ausgang, OFL-Import). Mit echter Pixel-Hardware und echten OFL-Dateien großer
Hersteller noch nicht ausprobiert.

## ✅ Iteration S – Looks, Compositions, Scenes (drei Stufen) 🧱
Konzept und Entscheidungen: [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md). Ersetzt Szenen mit Basis +
Kompositions-Kopien und Szenen auf Extra-Tasten durch drei Stufen: **Look** (feste Werte) → **Composition**
(1–n Schritte aus Looks mit Standard- und Schrittzeiten) → **Scene** (Menge geteilter Compositions plus Tasten 1–8).
Lauflicht, Regenbogen & Co. werden zu Generatoren, die Schritte erzeugen.

- ✅ S1 – Domain & Engine (Looks, Compositions, Szenen, Generatoren, Playback und Tasten)
- ✅ S2 – Datenbank `V19` & Umwandlung bestehender Projekte und Export-Dateien (Version 1 → 2)
- ✅ S3 – REST & WebSocket
- ✅ S4 – UI (Reiter „Looks“, Compositions mit Generator, Szenen mit Compositions und Tasten, Show-Modus, Cue-Liste)
- ✅ S5 – Doku nachgezogen

**DoD:** Ein bestehendes Projekt startet nach der Umwandlung und spielt im Show-Modus genauso wie vorher; eine neue
Szene aus drei geteilten Compositions (statisch, Farbwechsel aus drei Looks, Lauflicht per Generator) mit einer Taste
ist im Browser gebaut und gespielt.
Geprüft: Umwandlung einer echten Datenbank (Schema V12) und der Testdatenbank, im Browser (Testserver) eine Szene mit
Composition aus dem Programmer und einem erzeugten Regenbogen gebaut, Regenbogen auf Taste 1, im Show-Modus GO und
Taste – die DMX-Werte zeigen den Regenbogen über der Szene. Mit echter Hardware noch nicht gespielt.

## ✅ Iteration CR – Change Requests nach Iteration S (2026-10-06) 🛠️
- **Parallele Schritte:** Schritte einer Composition können zusammen starten („Start“: Begin / with … / after …).
  Sie bilden eine Zeile mit gemeinsamer Fade-/Hold-Zeit und stehen im Editor nebeneinander. Flyway `V20`
- **Fader spielen Compositions**, „not in cue list“ entfällt: bestehende Fader spielen die erste Composition ihrer
  Szene, Szenen außerhalb der Cue-Liste sind entfernt (Flyway `V21`, ebenso die Umwandlung alter Projekte und der
  Import von Export-Dateien der Version 1 und 2). Export Version 3
- **Programmer:** Fixture-Liste statt Kacheln (lange Namen lesbar), das Steuer-Panel bekommt den meisten Platz und
  ordnet seine Abschnitte in Spalten an
- **Scenes:** Hinweis auf den Weg über Compositions, „Save“ unten im Editor, Liste 30 % / Editor 70 %
- **Show:** Fortschritt als Stapel (davor, jetzt, nächste, übernächste kleiner und eingerückt, „+ n more“), die acht
  Tastenplätze sind immer reserviert, der DMX-Tab zeigt nur belegte Kanäle aller Universen, nach Fixture gruppiert
- **Party-Modus neu als Matrix** (ersetzt Paletten, Tap-Tempo und Party-Buttons): Felder mit Szene, Composition oder
  Text, Beschriftung, Füll- und Rahmenfarbe, Toggle/Flash, Gruppen (nur ein Feld an), höherer Wert gewinnt,
  Trennlinien zwischen Zeilen. Flyway `V22`, siehe [PARTY-MODE.md](PARTY-MODE.md)

**DoD:** `./build.sh` grün. Geprüft im Browser (Testserver): Composition mit parallelen Schritten, Start umstellen,
Szenen-Editor, Programmer-Liste, Party-Matrix einrichten und im Party-Modus schalten, Show-Stapel und DMX-Tab.

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
- ✅ **Formen in der Bühnenansicht:** Spot, Bar (LED-Bar) und Strip (LED-Band, z.B. DIY-ESP), jeweils mit Drehung
  und – bei Bar/Strip – Länge. Bar und Strip zeigen jeden Kopf eines Multi-Pixel-Geräts in seiner eigenen Farbe,
  „Auto“ wählt anhand von Kategorie und Köpfen. Flyway `V18`, `PUT /api/projects/{id}/fixtures/{fid}/stage-shape`,
  reist im Projekt-Export mit. Geprüft im Browser (Testserver: 8-Pixel-Bar, als Strip gedrehte Bar)
- Externe GO-Taste: USB-Fußschalter (als Tastatur), OSC (z.B. aus QLab); MIDI siehe eigene Iteration
- sACN als zweiter `DmxOutputPort`
- Hostnamen (mDNS, z.B. `artnet-dimmer.local`) als Art-Net-Ziel – noch offen, ob gewünscht
- Blitz-Effekt als Szenen-Attribut (Kerzen-/Feuerflackern ist jetzt Teil von Iteration K)
- PWA, Docker / Raspberry Pi als alternativer Betriebsort
- ✅ Import von Open-Fixture-Library-Profilen (Iteration G)
- ✅ **Druckbare Cue-Liste** für die Regie: „🖨 Cue list“ in der Szenenliste öffnet eine Druckseite (`#/print/cues`,
  hell, A4, Kopfzeile wiederholt sich auf jeder Seite): Nummer, Szene, „GO on“ (Regiehinweis), In/Out/Delay (eigene
  Zeiten fett), Auto-Follow, laufende Kompositionen, freigeschaltete Extra-Tasten, Abhak-Kästchen; darunter die
  Extra-Tasten. PDF über „Als PDF sichern“ im Druckdialog statt einer PDF-Bibliothek im Server

## 🧪 Iteration 11+ – Party-Modus 🪩 (Schritt 1 ersetzt durch die Party-Matrix, siehe Iteration CR)
Konzept und Entscheidungen: [PARTY-MODE.md](PARTY-MODE.md). Schritt 1 ist da und im UI als „experimental“ markiert,
überzeugt aber noch nicht. Zurückgestellt (2026-10-05): vor dem Weiterbauen wird die Bedienung neu überlegt.
Grob in Teilschritten:
1. ✅ **Tempo & Paletten:** dritter Betriebsmodus „Party“ (Server-Zustand, Programmer gesperrt, PIN wie im Betrieb,
   das Theater-Playback bleibt stehen und gibt nichts aus). Tap-Tempo (Taste `T`) mit BPM und Beat-Anzeige,
   10 feste Farbpaletten (RGB mit Weißanteil, Amber/UV/Warmweiß auf eigenen Emittern, sonst das nächste Farbrad-Fach),
   Intensität pro Gruppe, **Party-Buttons** (aus dem Programmer aufgenommen, Toggle/Flash, Tasten `1`–`9`) für Gobo,
   Prisma & Co. Party-Ebene Priorität 60, Flyway `V9`, Buttons reisen im Projekt-Export mit. Positionspaletten
   verschoben (kommen mit den Bewegungsbildern). Geprüft im Browser (Testserver) und per API-Test auf dem DMX-Ausgang
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
