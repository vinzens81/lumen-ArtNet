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
- A field can be **several columns wide** (setting "Width").
- **Gradient fields:** a field can show a gradient instead of playing something – **intensity** (dark → full) or
  **color** (red → orange → yellow → green → cyan → blue → violet, hue 0–300°; cyan/magenta/yellow on fixtures that
  subtract instead of mixing) for chosen fixtures. Touching or
  dragging along it sets the value of that point at once; the point is kept while the field is off. A gradient switches
  like a toggle field (the small "Off" switches it off) and takes part in groups: put it in the group of the color
  buttons and it replaces the button that is on. Fixtures that can show no mixed color are left alone by a color
  gradient.
- Every field plays its compositions from the moment it goes on. Grand master and blackout work as always.

## Behaviour

- A third operating mode "Party" as before: server state, all browsers follow, the programmer is locked, the theatre
  playback stands still and outputs nothing. Party layer with priority 60 above the playback.
- Fields stay on when you switch modes; another project starts with all fields off. If you change the matrix, a field
  stays on as long as it plays the same thing in the same place.
- If a scene or composition is deleted, its field becomes a text field; so does a gradient once all its fixtures are
  deleted.

## Quick setup (2026-10-12)

Optional help for freshly patched fixtures, in the patch: a "Quick setup" box in "Add fixture" (the choice is
remembered on the device), "Quick setup…" for fixtures that are already patched, or the side panel when several
fixtures of one type are selected (shift-click in the channel grid, checkboxes in the list view). For the chosen
fixtures together it makes:

- **Intensity looks** 0 / 50 / 100 % and **color looks** red, orange, green, blue, violet, each a shared look with a
  composition of one step (0.3 s fade, once). Each fixture shows a color the way it can (`FixtureColors`): red, green
  and blue (white, amber, UV and lime set to 0), else cyan, magenta and yellow, else the **color wheel slot** closest in
  hue – slots with a `color` in the profile that are no transition ("Blue → cyan"), at most 30° away; white and grey
  slots never stand in for a color. A color no slot comes close to is left out for that fixture (violet on a wheel
  with only blue).
- A **color palette**: a color preset per color ("<name> – Red", values per fixture), and the color looks refer to
  it – change the preset in the programmer and every look follows. Wheel slots none of the five colors took (e.g.
  yellow, white) become further presets, one per slot name, without a button. If the project would exceed 200
  presets, the looks are made without a palette.
- **Moves** line, circle and eight (fixtures with pan and tilt); the figures "Line", "Circle" and "Eight" of the
  project are used again if they exist.
- A **party row**: the name as a text, then a button per look and move, in the groups "<name> – intensity",
  "<name> – color" and "<name> – move", so one button switches all the fixtures.
- A **gradient row** below: intensity over 3 columns and color over 5, in the same groups as the buttons above. The
  color gradient takes fixtures that mix colors (red/green/blue or cyan/magenta/yellow); a wheel cannot run smoothly.

The rows go below the last used row (with a divider), the matrix grows up to 12 × 12; if it is full, nothing is made
and Lumen says so. Everything made is ordinary and can be changed or deleted.
- "All off" switches everything off.

## Implementation

- Domain: `PartyMatrix`, `PartyCell`, `PartySwitchboard` (on/off, groups, output with the maximum per attribute).
- Application: `PartyService` (control and output layer), `PartyMatrixService` (setup, checks references).
- REST: `GET/PUT /api/projects/{id}/party-matrix` (always the whole matrix), `GET /api/party`,
  `POST /api/party/cells/{row}/{column}/press|release|slide` (slide: `{"point": 0.4}`), `POST /api/party/clear`;
  WebSocket `party` (which fields are on, the points of the gradients), `partyMatrix` and the command `party.slide`
  (`row`, `column`, `point`) for dragging. The matrix travels with the project export (version 3; scenes by their
  number; `span` and `fader` are optional, so older files still read).
- Gradients: `PartyFader` in the domain; quick setup: `QuickSetupService`, `POST /api/projects/{id}/quick-setup`.
- Flyway `V22`: `party_matrix`, `party_cell`; `V28`: `party_cell.span`, `party_cell.fader_kind`, `party_cell_fixture`.

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
