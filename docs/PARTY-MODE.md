# Party-Modus (Zukunft)

> **Status:** Konzept für später. Wird erst nach dem Theater-Modus umgesetzt (siehe [ROADMAP.md](ROADMAP.md),
> Iteration 11). Die Architektur soll ihn aber von Anfang an ermöglichen.

Der Theater-Modus spielt eine **feste Szenenliste** per GO ab. Der Party-Modus ist das Gegenteil: **Live-Lichtsteuerung**
ohne Ablauf, wie an einem klassischen Lichtpult bei Konzerten oder in der Disco („Busking“). Spontan, rhythmisch
und effektlastig.

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
Die Effekt-Engine entsteht schon vorher als [Kompositionen](COMPOSITIONS.md) für dynamische Theater-Szenen
(Iteration K). Der Party-Modus ergänzt BPM-Kopplung und die Steuerung über Executors.

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
