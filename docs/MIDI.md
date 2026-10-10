# MIDI controller

A USB MIDI controller with faders, knobs and buttons (e.g. Akai APC mini, Korg nanoKONTROL2, Behringer X-Touch Mini,
Novation Launch Control) adds **real hardware** to the touch UI: faders you can grab and a GO button you can hit
without looking.

> **Status:** planned as an **optional iteration**, independent of the others. Useful from iteration 7 on, once show
> mode exists (see [ROADMAP.md](ROADMAP.md)).

## Where is the controller connected?

| Variant | Controller is plugged into … | Technology | When it makes sense |
|---|---|---|---|
| **A – server side** (start) | the computer running Lumen | `javax.sound.midi` (in the JDK, no dependency) | laptop at the control desk = server |
| **B – browser side** (later) | the tablet/laptop with the UI | Web MIDI API (Chrome/Edge) | server is somewhere else (e.g. a Pi in the rack) |

**Decision:** we start with **A**. It is a classic inbound adapter in the hexagon (`lumen-adapter-midi`) and works
even when no browser is open. Both variants use the same mapping logic in the application layer. Variant B only sends
the MIDI events to the server over the WebSocket.

```
 USB MIDI ─▶ lumen-adapter-midi ─▶ MidiInputUseCase ─▶ mapping ─▶ PlaybackUseCase / ProgrammerUseCase / …
                     ▲                                    │
                     └──── MidiFeedbackPort ◀─────────────┘   (LEDs, motor faders)
```

## Controller profiles vs. mapping

As with fixtures, we separate **what the hardware is** from **what it does in the project**:

**Controller profile (YAML, `midi-controllers/`)** describes the controls of a device:

```yaml
id: akai-apc-mini-mk2
name: Akai APC mini mk2
match: "APC mini mk2"            # substring of the MIDI device name for automatic detection
controls:
  - { id: fader-1, type: fader, message: cc, channel: 1, number: 48 }
  - { id: fader-9, type: fader, message: cc, channel: 1, number: 56 }
  - { id: pad-1,   type: button, message: note, channel: 1, number: 0,
      feedback: { off: 0, on: 5, blink: 2 } }       # velocity values for LED colors
  - { id: shift,   type: button, message: note, channel: 1, number: 122 }
```

A controller also works without a profile, just without names and without LED feedback (generic MIDI).

**Mapping (SQLite, per project)** defines which action a control triggers:

```
midi_mapping
  id, project_id
  controller_profile_id   TEXT            -- or 'generic'
  control_id              TEXT            -- 'fader-1' or 'cc:1:48'
  mode                    TEXT            -- 'show' | 'setup' | 'party' | 'all'
  action                  TEXT            -- see below
  target_id               INTEGER NULL    -- e.g. group or scene
  options_json            TEXT            -- curve, inversion, range
```

## Actions

| Action | Control | Mode |
|---|---|---|
| `playback.go` / `back` / `pause` / `finish` | button | show |
| `output.grand-master` | fader | all |
| `output.blackout` (toggle) · `output.flash-all` (hold) | button | all |
| `programmer.group-dimmer` (group) | fader | setup |
| `programmer.fixture-dimmer` (fixture) | fader | setup |
| `programmer.select-group` | button | setup |
| `programmer.attribute` (e.g. pan, tilt, color) of the selection | knob | setup |
| `scene.preview` / `scene.record` | button | setup |
| `party.field` (toggle/flash a field of the party matrix) | button | party |

In show mode only the actions of that mode are active. A programmer fader therefore cannot change anything during a
performance.

## Important details

- **MIDI learn:** click an action in the UI, then move the control on the controller. The mapping is done.
- **Soft takeover / pickup:** after a scene change, ordinary faders are no longer where the value is. A fader only
  takes over once it has "caught up" with the current value, so nothing jumps. The UI shows this (e.g. an arrow ↑/↓
  at the fader).
- **Feedback:** button LEDs show the state (GO ready, blackout active, selected group, running scene blinks). Motor
  faders follow, if present.
- **Resolution:** CC is 7 bit (0–127). That is enough for dimmers; 14-bit CC/pitch bend is supported when the
  profile says so.
- **Hotplug:** unplug and replug controllers without a restart (check the device list periodically). Status in the UI.
- **Debounce / double-GO protection:** the same lock time as the GO button in the UI.
- **macOS/Linux:** `javax.sound.midi` detects class-compliant USB devices. On Linux/Pi check the ALSA permissions if
  needed.

## UI

- **Settings → MIDI:** detected devices, profile assignment, mapping table, MIDI learn, monitor of incoming messages
  (for debugging)
- Small MIDI icon in the status bar: blinks on incoming messages

## Later

- Variant B (Web MIDI in the browser)
- MIDI clock as a tempo source for the [party mode](PARTY-MODE.md)
- MIDI Show Control / notes from sound software (e.g. QLab) as a GO trigger
- Ready-made mappings per profile as templates ("APC mini → party matrix")
