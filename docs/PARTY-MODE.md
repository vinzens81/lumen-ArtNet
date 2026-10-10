# Party-Modus

> **Status: umgesetzt als Party-Matrix (2026-10-06).** Der erste Versuch (Tap-Tempo, Farbpaletten, Intensität pro
> Gruppe, Party-Buttons aus dem Programmer) ist komplett ersetzt; seine Tabellen sind mit Flyway `V22` entfernt.
> Ab „Theater vs. Party“ steht die ursprüngliche Ideensammlung, sie ist nicht umgesetzt.

Der Theater-Modus spielt eine **feste Szenenliste** per GO ab. Der Party-Modus ist das Gegenteil: **Live-Lichtsteuerung**
ohne Ablauf – man schaltet vorbereitete Szenen und Compositions frei an und aus.

## Die Matrix

- Pro Projekt eine **Matrix** aus Zeilen × Spalten (je 1–12, Standard 4 × 4), eingerichtet im Setup-Reiter „Party“.
- Jedes **Feld** ist leer, ein **Text** (nur Beschriftung, z.B. eine Überschrift „Farben“), eine **Szene** (spielt alle
  ihre Compositions, ohne Fades und Tasten) oder eine **Composition**.
- Pro Feld: **Beschriftung** (leer = Name dessen, was es spielt), **Füllfarbe**, eigene **Rahmenfarbe**, **Toggle**
  (an/aus) oder **Flash** (an, solange gedrückt) und optional eine **Gruppe**.
- **Gruppen:** Von den Feldern einer Gruppe ist höchstens eines an – schaltet man Rot ein, geht Blau aus. Alles ohne
  Gruppe läuft beliebig parallel.
- **Kollisionen:** Setzen mehrere Felder dasselbe Attribut eines Fixtures, gewinnt der **höhere Wert** – für alle
  Attribute, nicht nur die Intensität.
- **Trennlinien** zwischen Zeilen gliedern die Matrix optisch (im Editor: Klick zwischen zwei Zeilen).
- Jedes Feld spielt seine Compositions ab dem Moment, in dem es angeht. Grand Master und Blackout wirken wie immer.

## Verhalten

- Dritter Betriebsmodus „Party“ wie bisher: Server-Zustand, alle Browser wechseln mit, der Programmer ist gesperrt,
  das Theater-Playback bleibt stehen und gibt nichts aus. Party-Ebene mit Priorität 60 über dem Playback.
- Felder bleiben an, wenn man den Modus wechselt; ein anderes Projekt beginnt mit allen Feldern aus. Ändert man die
  Matrix, bleibt ein Feld an, solange es an derselben Stelle dasselbe spielt.
- Wird eine Szene oder Composition gelöscht, wird ihr Feld zum Textfeld.
- „All off“ schaltet alles aus.

## Umsetzung

- Domain: `PartyMatrix`, `PartyCell`, `PartySwitchboard` (an/aus, Gruppen, Ausgabe mit Maximum pro Attribut).
- Anwendung: `PartyService` (Steuerung und Ausgabe-Ebene), `PartyMatrixService` (Einrichten, prüft Verweise).
- REST: `GET/PUT /api/projects/{id}/party-matrix` (immer die ganze Matrix), `GET /api/party`,
  `POST /api/party/cells/{row}/{column}/press|release`, `POST /api/party/clear`; WebSocket `party` (welche Felder an
  sind) und `partyMatrix`. Die Matrix reist im Projekt-Export mit (Version 3; Szenen über ihre Nummer).
- Flyway `V22`: `party_matrix`, `party_cell`.

## Theater vs. Party

| | Theater-Modus | Party-Modus |
|---|---|---|
| Ablauf | feste Szenenliste, GO | kein Ablauf, alles live |
| Zeitgefühl | Fade-Zeiten in Sekunden | Tempo in BPM, Effekte im Takt |
| Bedienung | ein GO-Button | viele Fader, Buttons, Flash-Tasten gleichzeitig |
| Inhalte | Szenen | Paletten, Effekte, Chaser |
| Fehlertoleranz | null, alles gesperrt | hoch, Experimentieren ist erwünscht |

Ein Projekt kann beides haben: Derselbe Patch wird im Theater- **oder** Party-Modus bespielt.

## Bausteine

### Paletten
Wiederverwendbare Werte, die man auf beliebige Gruppen „wirft“:
- **Farbpaletten:** Rot, Amber, Cyan, Magenta, UV, … (ein Tap = ganze Gruppe in der Farbe)
- **Positionspaletten** für Moving Heads: Publikum, Bühne, Decke, Kreuz
- **Strahlpaletten:** Gobo, Prisma, Zoom

### Effekte
Die Effekt-Engine entsteht schon vorher als Kompositionen für dynamische Theater-Szenen (Iteration K, heute
Generatoren für [Compositions](LOOKS-COMPOSITIONS-SCENES.md#generatoren-statt-vorlagen)). Der Party-Modus ergänzt BPM-Kopplung und die Steuerung über Executors.

Parametrisierbare Bewegungen über eine Gruppe, mit Phasenversatz zwischen den Geräten:
- **Intensität:** Pulse, Sinus, Sägezahn, Chase (Lauflicht), Random-Flackern, Strobe
- **Farbe:** Rainbow, Farbwechsel zwischen 2–n Farben, Farb-Chase
- **Position:** Bewegungsbilder (Kreis, Acht, Fächer, …) mit Offset, Scale, Rotation. Eigenes Konzept, siehe [MOVEMENT.md](MOVEMENT.md)
- Parameter: Geschwindigkeit (frei oder BPM-gekoppelt mit Teiler ½, 1, 2, 4), Größe, Phase/Spreizung, Richtung

### Chaser
Eine Folge von Schritten (Mini-Szenen), die im Takt abläuft: Loop, Ping-Pong, Random; Überblendung pro Schritt einstellbar.

### Executors (Playback-Fader)
Ein Raster frei belegbarer Slots. Jeder Slot hält eine Palette, einen Effekt oder einen Chaser und hat:
- einen **Fader** (Intensität/Größe des Effekts)
- **Flash** (gedrückt halten = 100 %) und **Toggle**
- optional eine Gruppe als Ziel

### Tempo
- **Tap-Tempo** (Button oder Taste `T`), BPM-Anzeige, Beat-Indikator
- später: **Beat-Erkennung** über das Mikrofon (Web Audio API im Browser → BPM/Beats an den Server)
- später: **MIDI-Clock** / Ableton Link

### Globale Live-Werkzeuge
- Grand Master, Blackout, **Flash All**, **Strobe All**, **Fog-Taste** (Nebelmaschine solange gedrückt)
- **Speed-Master** (alle Effekte schneller/langsamer)
- **Szenen speichern** des aktuellen Looks, um ihn später wieder abzurufen (auch als Theater-Szene nutzbar)

## UI-Skizze

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ◉ LUMEN  PARTY   ♩ 128 BPM ● ● ○ ○  [TAP]                Art-Net ● live         │
├──────────────────────┬─────────────────────────────────────────────────────────┤
│ GRUPPEN              │ FARBEN   🔴 🟠 🟡 🟢 🔵 🟣 ⚪  [Rainbow]                     │
│ [Pars] [Movers]      │ POSITION [Publikum] [Bühne] [Decke] [Kreis] [Acht]       │
│ [Bars] [Alle]        │ EFFEKTE  [Pulse] [Chase] [Flackern] [Strobe]             │
├──────────────────────┴─────────────────────────────────────────────────────────┤
│ EXECUTORS                                                                       │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                        │
│  │Warm│ │Chas│ │Rain│ │Kreis│ │Strb│ │Blnd│ │ —  │ │ —  │                        │
│  │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │    │ │    │                        │
│  │ ▮  │ │ ▮  │ │    │ │ ▮  │ │    │ │    │ │    │ │    │                        │
│  │[FL]│ │[FL]│ │[FL]│ │[FL]│ │[FL]│ │[FL]│ │    │ │    │                        │
│  └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│ MASTER ▮▮▮▮▮▮▮▯  SPEED ▮▮▮▮▯   [FLASH ALL] [STROBE ALL] [FOG]       [BLACKOUT]   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

Optik: noch mehr „Club“: Buttons glühen im Takt, Beat-Puls im Hintergrund, Neon-Akzente (Magenta statt Cyan).

## Was die Architektur schon jetzt vorbereiten muss

Damit der Party-Modus später **ohne Umbau** dazukommt, wird die DMX-Engine von Anfang an mit **Ausgabe-Ebenen
(Layern)** gebaut (siehe [ARCHITECTURE.md](ARCHITECTURE.md#dmx-engine)):

```
 Programmer ─┐
 Theater-    │      ┌──────────────┐     ┌────────────────┐     ┌──────────┐
 Playback  ──┼────▶ │ Layer-Merger │───▶ │ Master/Blackout│───▶ │ DmxFrame │
 (später)    │      │ HTP / LTP    │     └────────────────┘     └──────────┘
 Party-      │      └──────────────┘
 Executors ──┘
```

- Jede Quelle (Programmer, Theater-Playback, später Executors/Effekte) liefert pro Tick einen
  `OutputLayer` mit Attributwerten und einer Priorität.
- Der Merger kombiniert sie: Intensität HTP, übrige Attribute LTP nach Aktivierungszeitpunkt bzw. Priorität.
- Effekte sind reine Funktionen `f(zeit, beat, fixtureIndex, parameter) → Attributwerte`, also gut testbar.
- Die Zeitbasis (`Clock`) bekommt später eine **Beat-Clock** dazu.

## Datenmodell (Ausblick)

Neue Tabellen pro Projekt, z.B. `palette`, `effect`, `chaser`, `chaser_step`, `executor_slot`.
Wird erst mit Iteration 11 als Flyway-Migration ergänzt.
