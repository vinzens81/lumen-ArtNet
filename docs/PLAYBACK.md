# Scenes & playback

For a theatre play the show is an **ordered list of scenes**. The operator presses **GO** at the right moments, and
the light cross-fades into the next scene.

## Life cycle of a scene

Every scene goes through three phases:

| Phase | Duration | What happens |
|---|---|---|
| **Fade-in** | global `fade_in_ms` | the scene fades in from 0 to 100 % |
| **Stay** | until the next GO, or optionally `auto_follow_ms` | the scene stands |
| **Fade-out** | global `fade_out_ms` | the scene fades out to 0 % |

The fade times apply **project-wide** and are set in setup mode (default 3 s each). Single scenes can override them,
see [Fade times per scene](#fade-times-per-scene).

### Auto-follow (optional per scene)

By default a scene stands until someone presses GO. Per scene a **stay time** (`auto_follow_ms`) can be set as well.
Then the engine triggers the GO to the next scene by itself.

- The stay time starts **when the fade-in has finished**.
- A manual GO before it runs out takes precedence and cancels the countdown.
- **Pause** also holds the countdown. **Back** and **jump** reset it.
- Chains are possible (scene 5 → 5.1 → 5.2 automatically), e.g. for sequences like "sunset in three stages".
- The last scene of the list cannot have auto-follow. An ending ("fade to black") only happens on GO.

```
        GO                    automatic                automatic
        │                     │                        │
Sc. 5   │▁▂▄▆██████████████████▇▅▃▁                     │
        │ fade-in │ stay 8 s  │                        │
Sc. 5.1 │                     │▁▂▄▆█████████████████████▇▅▃▁
        │                     │ fade-in │ stay 10 s    │
Sc. 5.2 │                     │                        │▁▂▄▆████████… (until GO)
```

## Cross-fade: fade-out and fade-in run in parallel

A GO starts the fade-out of the current scene and the fade-in of the next one **at the same time**:

```
                  GO                                        GO
                  │                                         │
Scene 1  ▁▃▅▇█████│█▇▆▅▄▃▂▁                                  │
         fade-in  │ fade-out (5 s)                          │
                  │                                         │
Scene 2           │▁▂▄▆█████████████████████████████████████│█▇▆▅▄▃▂▁
                  │fade-in (3 s)   stay (until GO)          │ fade-out
                  │                                         │
Scene 3           │                                         │▁▂▄▆██████…
──────────────────┴─────────────────────────────────────────┴──────────▶ t
```

If the times differ (e.g. in 3 s, out 5 s), both start at GO and end at different moments.

## Fade curves

How fast it gets bright or dark over time is set by the **fade curve** – per project; a scene can have its own (it
applies to the GO into this scene, the fade-out at the end uses the project's):

| Curve | Intensity | Color, position, … | Effect |
|---|---|---|---|
| `linear` (default) | even in DMX value | even | looks fast at the beginning of a fade-in, slow at the end |
| `s-curve` | gentle start, fast in the middle, gentle end (smoothstep) | likewise | soft, "round" transitions |
| `square` | level², softer in the dark | even | looks more even to the eye, especially with LEDs |

The curve shapes the level (`L(t)`) of fade-in and fade-out. A fixture that is on in both scenes never goes through a
dip, see [How the engine mixes](#how-the-engine-mixes).

## Fade times per scene

In the scene editor, every scene can get its own times under "Fade times of the GO into this scene". An empty field
means: project time.

| Field | Effect |
|---|---|
| **In** | fade-in of this scene |
| **Out** | fade-out of the picture that is on stage at the GO **into this scene** |
| **Delay** | the fade-in starts this long after the GO, the fade-out starts right away |

- The times belong to the **transition into the scene**, as on common lighting desks. For a flash, the flash scene
  gets in 0 s and out 0 s. The GO after it takes the times of the following scene, e.g. the project times.
- **Back** and **jump** also take the times of the target scene. The "end" (fade to black) always takes the project
  time.
- The times are fixed at the GO: if the scene is edited during the fade, the fade runs to its end unchanged.
- The stay time (auto-follow) starts after delay + fade-in.
- A **light marker** in the script fades with the scene's fade-in and fade-out, but **without delay**: that belongs to
  the GO into the scene.
- During the delay, discrete channels (gobo, color wheel, …) keep their old value and only snap when the fade-in
  starts.

```
            GO
            │ delay 2 s │ fade-in 3 s │
Scene 4  ███│█▇▆▅▄▃▂▁   (fade-out 4 s, starts at GO)
Scene 5     │           │▁▂▄▆█████████████…
```

## How the engine mixes

Every running scene has a **level** `L(t)` between 0 and 1, determined by fade-in/-out. For every fixture and
attribute the render loop (~40 Hz) computes:

**Intensity (dimmer, also the virtual dimmer): directly from the old to the new value, without a dip**
```
getting brighter: dimmer = old + (new − old) × L_in(t)          (fade-in, after the delay)
getting darker:   dimmer = new + (old − new) × (1 − L_out(t))   (fade-out, from the GO)
```
- Fixture is on in both scenes → it goes directly from old to new, e.g. 30 % → 100 % only upwards. An HTP cross-fade
  (`max(old × L_out, new × L_in)`) would darken in between – with a delay or a short fade-out almost down to 0.
- Fixture only in the old scene → fades out with `fade_out_ms`.
- Fixture only in the new scene → fades in with `fade_in_ms`.

**Color, position, zoom, … (continuous): LTP – latest takes precedence, cross-faded**
```
value = start + (new_target − start) × progress_in(t)
```
- `start` = the value actually output at the moment of the GO.
- If the fixture was dark at the GO, the value jumps to the target at once (invisible, no "color smearing" while
  fading in).
- If an attribute is not set in the new scene, the previous value stays.

**Discrete channels (gobo, color wheel, prism, strobe, …):** snap to the new value at GO.

**Curves:** linear by default, optionally s-curve or square, see [Fade curves](#fade-curves).

## GO during a running fade

So that it never stutters, the engine works with a **snapshot**: at the GO the **current output state is frozen** and
faded out as the "old scene" with `fade_out_ms`. So there are never more than two layers running (snapshot + new
scene), and a quick double GO works without jumps.

## Controls in show mode

| Command | Key | Behaviour |
|---|---|---|
| **GO** | `Space` | Start the next scene in the list with a cross-fade |
| **Back** | `←` | Previous scene **in the list** (not the last one played) with the same fade times |
| **Jump** | tap a scene (or `Shift`+`↑`/`↓`) + GO | Pick any scene as the next one |
| **Pause** | `P` | Freeze running fades, pressing again continues |
| **Finish fade** | `Enter` | End the running fade at once (target state) |
| **Blackout** | `B` | Everything off at once (grand master 0), pressing again restores |
| **Grand master** | fader | Scales all intensities |

- **Show time:** the GO into the first scene starts a clock of how long the show has been running (real time, a
  pause does not stop it). It stops at the end of the show (and counts on after Back from the end), ■ Stop resets
  it. It is part of the playback status (`showTimeMs`, `showTimeRunning`) and not saved: after a server restart it
  starts again with the next show.
- **Before the first scene** the state is "blackout / audience entry". The first GO fades in scene 1.
- **After the last scene** a GO fades everything to black with `fade_out_ms` ("end").
- The current scene is saved in `playback_state` as soon as a GO starts its fade. A server that restarts in show mode
  continues there at once, before the first Art-Net frame, so nodes holding their last values never see a dark frame
  (the scene is set directly without a fade, its auto-follow countdown starts again). In setup mode the scene is only
  offered: "Start from the beginning" or a GO discards the offer.

## Implementation

- `Playback` (domain) computes on a **virtual clock** that stands still during pause – fades and the auto-follow
  countdown freeze and continue seamlessly.
- `Crossfade.mix` implements the mixing rules above; the fade behaviour per channel comes from the fixture profile
  (incl. `fade: snap|smooth`).
- The playback is an output layer (priority 50) **below** the programmer (100).
- Double-GO protection: client 300 ms, server 200 ms.
- When the server starts, the playback is "ready" (dark). If a scene was saved and the server comes back in show mode,
  it is restored before the render loop starts (`OutputConfiguration`). Otherwise the status names it as
  `resumeSceneId`; `POST /api/playback/resume` sets it (`Playback.restore`).

## Programmer vs. playback

- In **setup mode** the **programmer** sets values directly ("live editing"); scenes are saved or updated from it.
- In **show mode** only the playback outputs. The programmer is locked (the server rejects changes with 409), so that
  nobody changes anything by accident during the performance.
- When switching from setup to show, the programmer is cleared. The mode is server state: all connected browsers
  follow. Going back to setup mode needs a confirmation, or the PIN if one is set. The mode also survives a restart.

## Faders in the show (e.g. house lights)

Up to 8 **faders** per project each play a **composition** at the level they are pushed to – independent of GO, e.g.
house lights or work lights. They are assigned in the scene list ("Faders"); in show mode they sit below GO (slider,
plus "0" and "Full"). Scenes outside the cue list no longer exist (Flyway `V21`): a fader that played such a scene
before now plays its first composition, and the scene itself was removed.

- Mixing: intensity HTP (`max(stage, composition × level)`), other attributes are faded in with the level. Faders lie
  above playback and keys; grand master and blackout apply to them.
- The level applies in setup and show mode and does not change on GO. It is not saved: after a restart all faders
  are at 0.
- The fader's composition runs from the moment it is pushed up.

## Possible extensions (backlog)

- External GO button (USB foot switch / OSC); MIDI controller see [MIDI.md](MIDI.md)
