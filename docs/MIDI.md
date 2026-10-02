# MIDI-Controller

Ein USB-MIDI-Controller mit Fadern, Drehreglern und Buttons (z.B. Akai APC mini, Korg nanoKONTROL2,
Behringer X-Touch Mini, Novation Launch Control) ergänzt das Touch-UI um **echte Hardware**: Fader zum Anfassen
und ein GO-Button, den man blind trifft.

> **Status:** geplant als **optionale Iteration**, unabhängig von den anderen. Sinnvoll ab Iteration 7, wenn der
> Betriebsmodus existiert (siehe [ROADMAP.md](ROADMAP.md)).

## Wo wird der Controller angeschlossen?

| Variante | Controller hängt an … | Technik | Wann sinnvoll |
|---|---|---|---|
| **A – Server-seitig** (Start) | dem Rechner, auf dem Lumen läuft | `javax.sound.midi` (im JDK, keine Abhängigkeit) | Laptop am Regieplatz = Server |
| **B – Browser-seitig** (später) | dem Tablet/Laptop mit dem UI | Web MIDI API (Chrome/Edge) | Server steht woanders (z.B. Pi im Rack) |

**Entscheidung:** Wir starten mit **A**. Das ist ein klassischer eingehender Adapter im Hexagon
(`lumen-adapter-midi`) und funktioniert auch, wenn kein Browser offen ist. Beide Varianten nutzen dieselbe
Mapping-Logik in der Application-Schicht. Variante B schickt die MIDI-Events nur über den WebSocket an den Server.

```
 USB-MIDI ─▶ lumen-adapter-midi ─▶ MidiInputUseCase ─▶ Mapping ─▶ PlaybackUseCase / ProgrammerUseCase / …
                     ▲                                    │
                     └──── MidiFeedbackPort ◀─────────────┘   (LEDs, Motorfader)
```

## Controller-Profile vs. Mapping

Wie bei den Fixtures trennen wir **was die Hardware ist** von **was sie im Projekt tut**:

**Controller-Profil (YAML, `midi-controllers/`)** beschreibt die Bedienelemente eines Geräts:

```yaml
id: akai-apc-mini-mk2
name: Akai APC mini mk2
match: "APC mini mk2"            # Teilstring des MIDI-Gerätenamens zur automatischen Erkennung
controls:
  - { id: fader-1, type: fader, message: cc, channel: 1, number: 48 }
  - { id: fader-9, type: fader, message: cc, channel: 1, number: 56 }
  - { id: pad-1,   type: button, message: note, channel: 1, number: 0,
      feedback: { off: 0, on: 5, blink: 2 } }       # Velocity-Werte für LED-Farben
  - { id: shift,   type: button, message: note, channel: 1, number: 122 }
```

Ohne Profil funktioniert ein Controller auch, nur ohne Namen und ohne LED-Feedback (generisches MIDI).

**Mapping (SQLite, pro Projekt)** legt fest, welche Aktion ein Bedienelement auslöst:

```
midi_mapping
  id, project_id
  controller_profile_id   TEXT            -- oder 'generic'
  control_id              TEXT            -- 'fader-1' bzw. 'cc:1:48'
  mode                    TEXT            -- 'betrieb' | 'konfiguration' | 'party' | 'alle'
  action                  TEXT            -- siehe unten
  target_id               INTEGER NULL    -- z.B. Gruppe oder Szene
  options_json            TEXT            -- Kurve, Invertierung, Bereich
```

## Aktionen

| Aktion | Element | Modus |
|---|---|---|
| `playback.go` / `back` / `pause` / `finish` | Button | Betrieb |
| `output.grand-master` | Fader | alle |
| `output.blackout` (Toggle) · `output.flash-all` (gedrückt halten) | Button | alle |
| `programmer.group-dimmer` (Gruppe) | Fader | Konfiguration |
| `programmer.fixture-dimmer` (Fixture) | Fader | Konfiguration |
| `programmer.select-group` | Button | Konfiguration |
| `programmer.attribute` (z.B. Pan, Tilt, Farbe) der Auswahl | Drehregler | Konfiguration |
| `scene.preview` / `scene.record` | Button | Konfiguration |
| `party.executor-fader` / `-flash` / `-toggle`, `party.tap-tempo` | Fader/Button | Party (Zukunft) |

Im Betriebsmodus sind nur die Aktionen dieses Modus aktiv. Ein Programmer-Fader kann während der Vorstellung
also nichts verstellen.

## Wichtige Details

- **MIDI-Learn:** Im UI eine Aktion anklicken, dann am Controller das Element bewegen. Fertig ist die Zuordnung.
- **Soft-Takeover / Pickup:** Normale Fader stehen nach einem Szenenwechsel nicht mehr dort, wo der Wert ist.
  Ein Fader übernimmt erst, wenn er den aktuellen Wert „eingeholt“ hat, damit nichts springt. Das UI zeigt das an
  (z.B. Pfeil ↑/↓ am Fader).
- **Feedback:** Button-LEDs zeigen den Zustand (GO bereit, Blackout aktiv, ausgewählte Gruppe, laufende Szene blinkt).
  Motorfader werden nachgeführt, falls vorhanden.
- **Auflösung:** CC ist 7 Bit (0–127). Das reicht für Dimmer; 14-Bit-CC/Pitch-Bend wird unterstützt, wenn das
  Profil es angibt.
- **Hotplug:** Controller ab- und wieder anstecken, ohne Neustart (Geräteliste periodisch prüfen). Status im UI.
- **Entprellen / Doppel-GO-Schutz:** dieselbe Sperrzeit wie der GO-Button im UI.
- **macOS/Linux:** `javax.sound.midi` erkennt class-compliant USB-Geräte. Auf Linux/Pi ggf. ALSA-Rechte prüfen.

## UI

- **Einstellungen → MIDI:** erkannte Geräte, Profil-Zuordnung, Mapping-Tabelle, MIDI-Learn, Monitor der eingehenden
  Nachrichten (zum Debuggen)
- Kleines MIDI-Symbol in der Statusleiste: blinkt bei eingehenden Nachrichten

## Später

- Variante B (Web MIDI im Browser)
- MIDI-Clock als Tempo-Quelle für den [Party-Modus](PARTY-MODE.md)
- MIDI Show Control / Notes von einer Ton-Software (z.B. QLab) als GO-Trigger
- Fertige Mappings je Profil als Vorlage („APC mini → Party-Executors“)
