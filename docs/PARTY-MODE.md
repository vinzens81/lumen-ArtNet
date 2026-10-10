# Party mode

> **Status: implemented as the party matrix (2026-10-06).** The first attempt (tap tempo, color palettes, intensity
> per group, party buttons from the programmer) is completely replaced; its tables were removed with Flyway `V22`.
> From "Theatre vs. party" on, the original collection of ideas follows; it is not implemented.

Theatre mode plays a **fixed scene list** with GO. Party mode is the opposite: **live lighting control** without a
running order – you switch prepared scenes and compositions on and off freely.

## The matrix

- One **matrix** of rows × columns per project (1–12 each, default 4 × 4), set up in the setup tab "Party".
- Every **field** is empty, a **text** (just a label, e.g. a heading "Colors"), a **scene** (plays all its
  compositions, without fades and keys) or a **composition**.
- Per field: **label** (empty = the name of what it plays), **fill color**, its own **border color**, **toggle**
  (on/off) or **flash** (on while pressed) and optionally a **group**.
- **Groups:** of the fields of a group, at most one is on – switch on red and blue goes off. Everything without a group
  runs in parallel freely.
- **Collisions:** if several fields set the same attribute of a fixture, the **higher value** wins – for all
  attributes, not just intensity.
- **Dividers** between rows structure the matrix visually (in the editor: click between two rows).
- Every field plays its compositions from the moment it goes on. Grand master and blackout work as always.

## Behaviour

- A third operating mode "Party" as before: server state, all browsers follow, the programmer is locked, the theatre
  playback stands still and outputs nothing. Party layer with priority 60 above the playback.
- Fields stay on when you switch modes; another project starts with all fields off. If you change the matrix, a field
  stays on as long as it plays the same thing in the same place.
- If a scene or composition is deleted, its field becomes a text field.
- "All off" switches everything off.

## Implementation

- Domain: `PartyMatrix`, `PartyCell`, `PartySwitchboard` (on/off, groups, output with the maximum per attribute).
- Application: `PartyService` (control and output layer), `PartyMatrixService` (setup, checks references).
- REST: `GET/PUT /api/projects/{id}/party-matrix` (always the whole matrix), `GET /api/party`,
  `POST /api/party/cells/{row}/{column}/press|release`, `POST /api/party/clear`; WebSocket `party` (which fields are
  on) and `partyMatrix`. The matrix travels with the project export (version 3; scenes by their number).
- Flyway `V22`: `party_matrix`, `party_cell`.

## Theatre vs. party

| | Theatre mode | Party mode |
|---|---|---|
| Running order | fixed scene list, GO | none, everything live |
| Sense of time | fade times in seconds | tempo in BPM, effects on the beat |
| Operation | one GO button | many faders, buttons, flash keys at once |
| Content | scenes | palettes, effects, chasers |
| Fault tolerance | zero, everything locked | high, experimenting is welcome |

A project can have both: the same patch is played in theatre **or** party mode.

## Building blocks

### Palettes
Reusable values that you "throw" onto any group:
- **Color palettes:** red, amber, cyan, magenta, UV, … (one tap = the whole group in that color)
- **Position palettes** for moving heads: audience, stage, ceiling, cross
- **Beam palettes:** gobo, prism, zoom

### Effects
The effect engine is built earlier as compositions for dynamic theatre scenes (iteration K, today generators for
[compositions](LOOKS-COMPOSITIONS-SCENES.md#generators-instead-of-templates)). Party mode adds BPM coupling and
control through executors.

Parametrizable movements across a group, with a phase offset between the fixtures:
- **Intensity:** pulse, sine, sawtooth, chase, random flicker, strobe
- **Color:** rainbow, color change between 2–n colors, color chase
- **Position:** movement figures (circle, eight, fan, …) with offset, scale, rotation. A concept of its own, see
  [MOVEMENT.md](MOVEMENT.md)
- Parameters: speed (free or BPM-coupled with divider ½, 1, 2, 4), size, phase/spread, direction

### Chaser
A sequence of steps (mini scenes) that runs on the beat: loop, ping-pong, random; cross-fade adjustable per step.

### Executors (playback faders)
A grid of freely assignable slots. Every slot holds a palette, an effect or a chaser and has:
- a **fader** (intensity/size of the effect)
- **flash** (hold = 100 %) and **toggle**
- optionally a group as target

### Tempo
- **Tap tempo** (button or key `T`), BPM display, beat indicator
- later: **beat detection** via the microphone (Web Audio API in the browser → BPM/beats to the server)
- later: **MIDI clock** / Ableton Link

### Global live tools
- Grand master, blackout, **flash all**, **strobe all**, **fog key** (fog machine while pressed)
- **Speed master** (all effects faster/slower)
- **Save scenes** of the current look to recall it later (also usable as a theatre scene)

## UI sketch

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ◉ LUMEN  PARTY   ♩ 128 BPM ● ● ○ ○  [TAP]                Art-Net ● live         │
├──────────────────────┬─────────────────────────────────────────────────────────┤
│ GROUPS               │ COLORS   🔴 🟠 🟡 🟢 🔵 🟣 ⚪  [Rainbow]                     │
│ [Pars] [Movers]      │ POSITION [Audience] [Stage] [Ceiling] [Circle] [Eight]   │
│ [Bars] [All]         │ EFFECTS  [Pulse] [Chase] [Flicker] [Strobe]              │
├──────────────────────┴─────────────────────────────────────────────────────────┤
│ EXECUTORS                                                                       │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                        │
│  │Warm│ │Chas│ │Rain│ │Circ│ │Strb│ │Blnd│ │ —  │ │ —  │                        │
│  │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │ ▮  │ │    │ │    │                        │
│  │ ▮  │ │ ▮  │ │    │ │ ▮  │ │    │ │    │ │    │ │    │                        │
│  │[FL]│ │[FL]│ │[FL]│ │[FL]│ │[FL]│ │[FL]│ │    │ │    │                        │
│  └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│ MASTER ▮▮▮▮▮▮▮▯  SPEED ▮▮▮▮▯   [FLASH ALL] [STROBE ALL] [FOG]       [BLACKOUT]   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

Look: even more "club": buttons glow on the beat, a beat pulse in the background, neon accents (magenta instead of
cyan).

## What the architecture has to prepare now

So that party mode can be added later **without rebuilding**, the DMX engine is built with **output layers** from
the start (see [ARCHITECTURE.md](ARCHITECTURE.md#dmx-engine)):

```
 Programmer ─┐
 Theatre     │      ┌──────────────┐     ┌────────────────┐     ┌──────────┐
 playback  ──┼────▶ │ Layer merger │───▶ │ Master/blackout│───▶ │ DmxFrame │
 (later)     │      │ HTP / LTP    │     └────────────────┘     └──────────┘
 Party       │      └──────────────┘
 executors ──┘
```

- Every source (programmer, theatre playback, later executors/effects) delivers an `OutputLayer` with attribute
  values and a priority on every tick.
- The merger combines them: intensity HTP, other attributes LTP by activation time or priority.
- Effects are pure functions `f(time, beat, fixtureIndex, parameters) → attribute values`, so easy to test.
- The time base (`Clock`) later gets a **beat clock** in addition.

## Data model (outlook)

New tables per project, e.g. `palette`, `effect`, `chaser`, `chaser_step`, `executor_slot`.
Only added as a Flyway migration with iteration 11.
