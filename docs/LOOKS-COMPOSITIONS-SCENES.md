# Looks, compositions, scenes – three levels

> **Status:** implemented (iteration S, see [ROADMAP.md](ROADMAP.md)). Replaces the model from iteration K
> (scene = base + copies of compositions, scenes on extra keys).
> Deviations from the plan are listed under [Implementation](#implementation).

## Why

There used to be too many ways to the same goal: fixed values in the scene, copies of compositions in the scene,
library compositions, templates with parameters, scenes on extra keys and scenes only for keys (scenes in scenes).
The new model has exactly three levels, each building on the one below:

```
Scene 4 "Fair"   (GO list: number, cue note, fades, delay, auto-follow, script)
├── Compositions  (each runs on its own, order = priority)
│   ├── "Base light warm"    1 step    → look "Acting area warm"
│   ├── "Colorful stalls"    3 steps   → looks red / yellow / green · fade 2 s, hold 4 s (step 2: hold 8 s)
│   └── "Fairground chase"   8 steps, created by the generator "Chase" on group "Footlights"
└── Keys 1–8
    ├── 1 "Thunderstorm"   flash
    └── 2 "Police"         toggle, fade 0.5 s
```

## Level 1 – look

A **look** is a simple setting: fixed values per fixture and attribute (0–1), plus the presets they come from (as
scenes had before).

- Record with "Record look" in the programmer; "Load" brings it into the programmer, "Update" saves back.
- The **history** (revisions, undo) belongs to the look instead of the scene.
- **Shared looks** are listed in the "Looks" tab and can be part of several compositions. A change applies
  everywhere, the list shows "used in 3 compositions". A look in use cannot be deleted.
- **A composition's own looks:** looks created by "Record step" or by a generator belong to their composition
  (`owner`). They only appear there, not in the "Looks" tab – otherwise a chase over 8 fixtures would fill the list
  with 8 looks. "Make shared" turns an own look into a shared one. When the composition is deleted, its own looks go
  with it.

## Level 2 – composition

A **composition** is a sequence of 1–n steps. Every step refers to a look.

- **Default times of the composition:** fade and hold, plus the play mode **loop**, **ping-pong** or **once** (runs
  through once and stays at the last step, e.g. a sunrise).
- **Overridable per step:** fade and/or hold. Empty = default time (the editor shows it as a placeholder).
- A composition with one step is a static light.
- **Intensity absolute or relative:** absolute (default) sets the brightness, HTP applies with other compositions.
  Relative scales the brightness of what lies below it in the scene (candle flicker around a base light).
- **Shared:** a composition can be part of any number of scenes. A change applies in all scenes. For a variant there
  is "Duplicate". There are no copies and no distinction between library and scene any more.
- **Per head** for multi-pixel fixtures stays (values like `red@3` in the looks).

### Parallel steps

Steps can run **at the same time** (change request of 2026-10-06): every step has a "Start" – after the step before
(default), "Begin" or together with another row. Steps starting at the same time form a **row** and stand next to
each other in the editor. Decided: a row has **one** fade and hold time (that of its first step), so that "Start
after" stays unambiguous; loop, ping-pong and once run across the rows. Only `composition_step.parallel` is stored
(starts with the step before, Flyway `V20`). If two looks of a row meet on a fixture, HTP applies for intensity and the
later step for everything else.

Rules within the steps (as for step sequences before): a fixture of the composition that does not appear in a step or
has no intensity there delivers intensity 0 in that step. With HTP the base light of other compositions stays. Other
attributes keep their last value.

### Generators instead of templates

Chase, color change, rainbow, wave and flicker are no longer a kind of composition of their own, but **generators**:
they create the steps (and their own looks) of a perfectly normal composition. Afterwards every step can be edited
like any other.

| Generator | Settings | Creates |
|---|---|---|
| Chase | tempo (s per step), width, direction (forward / backward / ping-pong / random), soft ↔ hard, min/max | one step per fixture/head (or per position when width > 1), hold/fade from tempo and "soft" |
| Color change | 2–n colors with hold and fade, spread (0 = all the same, 100 % = color chase) | one step per color with its hold and fade; with spread, colors offset per fixture in every step |
| Rainbow | tempo (s per cycle), saturation, spread | 12 hues with fades, spread across the fixtures |
| Wave | shape (sine, triangle, sawtooth, square), tempo, min/max, spread | 16 points of one period with fades (a sine becomes a smoothed polygon) |
| Flicker | character (candle, fire, broken tube), strength, tempo, fixed random seed | about 40 random brightnesses with short, irregular times, intensity relative |

The target of a generator is a group or a fixed list of fixtures; the order defines direction and spread. A generator
only sets attributes the fixtures have – a color change on a plain dimmer is reported as a hint in the editor instead
of silently doing nothing.

Consequences worth knowing:
- **Groups are no longer dynamic:** the steps contain fixed fixtures. If the group changes, you have to generate
  again. To keep that convenient, the composition remembers the **generator settings** (`generator`, only for
  generating again, without influence on playing): "Regenerate" creates the steps again with changed settings. Steps
  changed by hand are lost then; the editor warns.
- **Flicker repeats** after its ~40 steps (a few seconds). Thanks to the irregular times this is hardly noticeable;
  generate more steps if needed.
- Soft transitions are cross-faded linearly instead of computed as a curve. That is good enough for stage lighting.

## Level 3 – scene

A **scene** is a set of compositions, plus, as before, number, name, cue note, fade-in/-out, delay, fade curve,
auto-follow and script.

- **Per use** of a composition in the scene: **level** (0–100 %), **tempo factor** and **"Restart at GO"**.
  The order is the priority: for color and other attributes the later one wins (LTP), intensity mixes HTP.
- **Independent:** every composition has its own clock from the GO of the scene.
- **No fixed base any more:** the base light is a composition with one step.
- **Continuing:** if the same composition is also part of the next scene, it continues seamlessly (no phase jump,
  the level glides over the fade time), unless "Restart at GO" is set. The basis is now simply the composition itself
  instead of the origin of a copy.
- **Shortcut "Record scene"** in the programmer: creates look, composition (1 step) and scene in one click. Simple
  scenes are as quick to build as before. "Update" in the scene updates the look of its first composition if that has
  only one step.
- **No scenes in scenes:** scenes on extra keys no longer exist.
- **Faders** (house lights, work lights) play a **composition**. Scenes outside the GO list no longer exist (change
  request 2026-10-06, Flyway `V21`): a fader that played such a scene plays its first composition, and the scene was
  removed.

### Light markers and looks (2026-10-06)

A line `//## Name (info text)##` in the script divides the scene into **parts**: the start (before the first marker)
and one after every marker. Every part has its own compositions and looks and stands on its own. When the script
position in the show passes a marker (also backwards), `Playback.goToSection` cross-fades with the fade times of the
scene; what is in both parts keeps running and does not move. Parts are stored by the marker name
(`scene_composition.section`, `scene_look`); markers with the same name share their light. Scenes can also play
shared **looks** directly (`SceneLook`, with level), below the compositions.

### Keys

- Every scene has its own **keys 1–8**. Every key plays a composition, **toggle** or **flash**, with its own fade
  time. A new scene takes over the keys of the scene before it.
- **GO:** a key that is on stays on if the same composition lies on a key in the new scene (also on another number);
  otherwise it fades out. At the end of the show all keys go off.
- Mixing as before: keys lie above the scene, intensity HTP or relative, color LTP with level × fade; with several keys
  the higher number wins. Blackout and grand master apply to them too. Keys run on the real clock; pause does not stop
  them.

## Mixing and time

These rules come from iteration K and apply unchanged to compositions in scenes, on keys and on faders.

**Mixing per fixture and attribute** (before the layer merger and the cross-fade logic):
- **Intensity absolute:** HTP, `max(below, composition × level)` – a base light stays, a chase "flashes over it".
- **Intensity relative** (flicker): modulates what lies below, `below × factor(t)` – the candle flickers around the
  programmed brightness.
- **Color and other attributes (LTP):** `below + (composition − below) × level`. At level 50 % a color change lies
  "half" over the base color. If several compositions act on the same attribute, the later one wins; the scene editor
  shows such overlaps.

**Time, cross-fade and playback:**
- Compositions of the scene run on the **virtual playback clock**: **pause** also freezes chases and color changes,
  "Finish fade" only affects the scene transition, not the sequence.
- **Fade-in:** the composition runs from the GO (or from the end of the delay) and is faded in with the scene level.
- **Fade-out:** the compositions of the old scene **keep running while fading out** and get darker instead of
  freezing. On a double GO, older compositions still fading out are taken into the snapshot, so that at most two
  layers run.
- For the cross-fade the values of a composition are "LTP targets that move": `Crossfade.mix` gets the **current**
  target values on every tick. The rule "if the fixture was dark at GO, the color jumps at once" stays.

```
            GO (sc. 4)                                  GO (sc. 5)
            │                                           │
Base light  │▁▂▄▆██████████████████████████████████████│█▇▅▃▁
Chase       │▁▂▄▆█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█│▇▁▅▁▃▁▁       keeps running while fading out
Color change│▁▂▄▆ red → yellow → green → red → yellow →│→ red → …    also in sc. 5: continues seamlessly
```

## What stays the same

Programmer, presets (now referenced from looks instead of scenes), faders (today they play compositions), party mode
(today a matrix, see [PARTY-MODE.md](PARTY-MODE.md)), fade logic and cross-fade, "Show on stage" (for look,
composition and scene), rehearsal cross-fade, printable cue list.

## Data model (Flyway V19)

```
look
  id, project_id, name
  owner_composition_id  FK → composition ON DELETE CASCADE NULL   -- set = a composition's own look
look_value(look_id, fixture_id, attribute, value)                  -- replaces scene_value
look_preset(look_id, fixture_id, attribute_group, preset_id)       -- replaces scene_preset
look_revision, look_revision_value, look_revision_preset           -- replace scene_revision*

composition
  id, project_id, name
  fade_ms, hold_ms                         -- default times
  play_mode   'loop' | 'ping_pong' | 'once'
  intensity   'absolute' | 'relative'
  per_head
  generator   TEXT NULL                    -- JSON: generator settings, only for "Regenerate"
composition_step(composition_id, step_index, look_id, fade_ms NULL, hold_ms NULL)

scene                                       -- as before, key_only → in_cue_list (inverted)
scene_composition(scene_id, composition_id, position, level, speed_factor, restart)
scene_key(scene_id, slot 1–8, composition_id, key_mode, fade_ms)

dropped: scene_value, scene_preset, scene_revision*, composition.scene_id/key_slot/source_id/kind/params/target/
         group_id/level/speed_factor/restart/once/key_mode/key_fade_ms, composition_target,
         composition_step_value, scene_extra_key, extra_key_scene
```

## Converting existing projects

Once, at the first start with the new version; Lumen creates a backup automatically before. The same converter also
reads old export files.

1. **Fixed values of a scene** → composition "4 Fair" (1 step) with its own look in first place of the scene. Presets
   and history come along.
2. **Copies of compositions in scenes** → copies with the same origin and the same content become **one** composition
   (so it keeps continuing seamlessly from scene to scene). Differing copies become compositions of their own
   ("Fairground chase (4)"). Level, tempo and "restart" move into `scene_composition`.
3. **Step sequences** → their steps become the composition's own looks.
4. **Templates** (chase etc.) → converted into steps with the matching generator; the settings stay for "Regenerate".
   For group targets, the members at the time of conversion apply.
5. **Library compositions** → compositions (in no scene).
6. **Extra keys with a composition** → a key in every scene in which it was enabled.
7. **Scenes on keys** and scenes only for keys: if the scene consists of one composition, it goes onto the key. If it
   consists of several, Lumen spreads them over free keys of the same scenes; if there is not enough room, the key
   stays empty. What was rebuilt or left out is logged as a warning at **start**.
8. **Scenes only for keys** that a fader plays stay as scenes "not in cue list". The others are no longer needed
   after the conversion and are also kept as "not in cue list" – they can be deleted by hand.

## UI

- **Tabs (setup):** Programmer · **Looks** (new) · Compositions · Scenes
- **Programmer:** "Record look", "Record scene" (shortcut), "Record step" (into the open composition)
- **Looks:** list with color preview and "used in …"; load, update, rename, duplicate, delete, history
- **Compositions:** list with animated preview. Editor: default times and play mode; step list, a look per step
  (shared or own), "Record step", fade/hold with placeholder; "Generate…" opens the generator, "Regenerate"; live
  preview and "Show on stage"; "used in scenes …"
- **Scenes:** scene editor with compositions (add existing, new from the programmer, new by generator), order by drag
  & drop, level, tempo, restart; section "Keys 1–8"; times and script as before
- **Show mode:** unchanged; the key bar shows the keys of the current scene with names

## Implementation plan (iteration S)

On a branch of its own, every step with a green `./build.sh`.

1. **S1 – domain & engine:** look, composition with look steps and default times, scene with compositions and keys;
   generators as pure functions (table tests); `Playback` and keys on the new model (tested with `Clock`). The
   existing engine stays: scenes no longer have a base, everything runs through compositions.
2. **S2 – database & conversion:** Flyway `V19` with a Java converter, tests against a database in the old format;
   export version 2, import of old files through the same converter.
3. **S3 – REST & WebSocket** for looks, compositions, scenes and keys; old endpoints are dropped.
4. **S4 – UI:** looks, composition editor with generator, scene editor with keys, show mode; remove old ways.
5. **S5 – docs:** update PLAYBACK.md, UI.md, PROJECT-DATA.md; the old concept (COMPOSITIONS.md) has since been merged
   into this document.

## Decisions (2026-10-06)

| Topic | Decision |
|---|---|
| Templates (chase, rainbow, …) | **generators** that create the steps of a normal composition; no kind of their own |
| Composition in several scenes | **shared**: a change applies in all scenes; per use only level, tempo, restart |
| Key at GO | stays on if the same composition lies on a key in the new scene |
| Fader | plays a **composition** (since 2026-10-06; before a scene "not in cue list") |

## Implementation

- **Domain:** `NamedLook` (look with name and optional owner) and `LookRevision` in the package `look`;
  `Composition`, `CompositionStep`, `CompositionSpec` (default times, `PlayMode`, `IntensityMode`,
  `GeneratorSettings`); `Scene` with `SceneComposition` and `SceneKey`. The engine stayed: a composition becomes the
  `StepSequence` of its looks (`Composition.sequence`), `Playback` plays scenes without values of their own, only with
  compositions. Continuing on the same composition ID. `ExtraKeys` takes the keys per scene and carries a key that is
  on over if its composition lies on any key in the new scene; otherwise it fades out with its fade time.
- **Generators** (`Generator`) sample the former effects: a hard chase plays exactly as before, soft transitions are
  cross-faded linearly. Very fast effects get fewer steps, so that no step is shorter than 20 ms.
- **Own looks:** the shortcut "Record scene" and the conversion also create the look as the new composition's own look
  (not as a shared one) – otherwise the "Looks" tab would fill up with one look per scene. "◆ Make shared" makes it
  shared when needed.
- **Conversion:** Flyway `V19` renames the old tables to `legacy_*`, `LegacyMigrationService` converts every project
  once at start (with a backup `…-before-looks.db` before) and remembers it in `legacy_conversion`. Old export files
  (version 1) go through the same `LegacyConverter`. Color effects only get their own intensity if none of the scenes
  they run in gives the fixtures an intensity (before, it applied only without a base intensity). Level and tempo of
  a key no longer exist; differing values are logged. Checked with a real database (schema V12): scenes,
  compositions, keys and the resume scene arrived correctly.
- **REST:** `/looks` (CRUD, `record`, `revisions`, `restore`, `load`, `duplicate`), `/compositions` (CRUD,
  `duplicate`, `record-step`, `generate`), `/scenes/{id}/compositions` and `/scenes/{id}/keys` (PUT, whole list).
  Dropped: `/extra-keys`, `/scenes/{id}/revisions`, `reload-from-library`, `save-to-library`. WebSocket topic
  `looks`. Export version 2.
- **UI:** new tab "Looks", composition editor with steps from looks and generator (`EffectForm`), scene editor with
  compositions and keys. There is no preview step before generating: generating replaces the steps at once, the live
  preview shows the result, "Generate steps again" changes it.
