# Web interface – concept

The interface has two clearly separated modes, **Setup** and **Show**:

| | **Setup** (setting up & rehearsals) | **Show** (performance) |
|---|---|---|
| Who/when | crew during setup and rehearsals | operator during the performance |
| Goal | set up the project, build scenes | press GO safely and without mistakes |
| Editing | everything | nothing. Only playback, master, blackout |
| Look | information-rich | reduced, large elements, extremely dark |

The third mode is **Party** (live control from a matrix of fields), see [PARTY-MODE.md](PARTY-MODE.md). The app shell
was therefore designed for more than two modes from the start.

The mode is switched with a switch at the top right. **Show → Setup** requires the **setup PIN**, so that it cannot
happen by accident during the performance. If no PIN is set, a confirmation is enough. With a PIN, the header shows
"🔒 Locked" or "🔓 Unlocked" in setup mode; a locked change opens the PIN field (with a number pad for touch).

## Look & feel

**Guiding idea: "The UI glows like the light."**

- **Dark mode first** (`#0b0d12`). Even darker in show mode, so that the screen does not disturb in the auditorium.
- **Glow instead of areas:** fixtures and scene tiles shine in their color/brightness.
- **Glassmorphism** for panels, fine 1px borders, accent color neon cyan `#3cf0ff`.
- **Mode color:** setup = cyan accent, show = amber/red accent. The mode is recognizable at a glance.
- **Typography:** `Inter` for text, `JetBrains Mono` for addresses, scene numbers and times.
- **Touch-first:** large faders and buttons, a tablet in landscape as the desk.

---

## Mode 1: Setup

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ◉ LUMEN  "The Tempest" ▾   [Patch] [Stage] [Looks] [Compositions] [Scenes] [Universes] [⚙]  SETUP ◐ SHOW │
├────────────┬──────────────────────────────────────────────────┬────────────────┤
│ GROUPS     │                                                  │ PROGRAMMER     │
│ ▣ All      │         STAGE VIEW / LIST                        │ Front left     │
│ ▣ Front    │                                                  │ ────────────── │
│ ▣ Sides    │      ◯ Spot M (light cone)                       │  (color wheel) │
│ ▣ Back-    │                                                  │ Dimmer ▮▮▮▮▯   │
│   ground   │   ◉ FL      ◉ FM       ◉ FR                      │ Pan/Tilt [XY]  │
│            │  (warm)    (off)     (warm)                      │ Raw channels ▸ │
├────────────┴──────────────────────────────────────────────────┴────────────────┤
│ Scene 4 "Storm rising"   [Record ⏺] [Update] [Clear programmer]                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Tabs
- **Project selection** (top left): switch projects. In the tab **Projects**: create, edit, export (download as
  `.lumen.json`) and import.
- **Universes:** create Art-Net targets (IP/broadcast, net/subnet/universe), node discovery.
- **Patch:**
  - Browse the fixture library on the left, add a fixture with "+" or drag & drop (name, mode, universe, address,
    count). The next free address is suggested.
  - **Address grid** (512 cells per universe) shows used ranges in color, overlaps in red.
  - Assign groups.
  - "⇣ Import from Open Fixture Library" below the library takes a fixture from open-fixture-library.org
    ([FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md#import-from-the-open-fixture-library)); warnings appear in the dialog.
- **Stage:** place fixtures on a stage floor plan by drag & drop (optional background image). Upstage is at the top,
  the audience at the bottom. The fixtures glow like the real output; those not placed are listed on the left.
  Every fixture has a **shape**: spot (round with light spot), bar (LED bar, one segment per head) or strip (LED tape
  as a thin pixel line). "Auto" draws fixtures of category `bar` and multi-pixel fixtures as a bar, all others as a
  spot. For the selected fixture, shape, rotation and length can be set on the left; `R` rotates by 15°. On the right
  above the stage, the control panel of the programmer appears as an overlay (the same component as in the Programmer
  tab, one column); ✕ hides it, "🎛 Controls" shows it again, another fixture opens it again.
- **Looks:** fixed values from the programmer ("⏺ Record look"), with color preview, "Load into programmer", "Update
  from programmer" (replace or merge), history with "↶ Undo"/"Restore", duplicate and delete (not while a composition
  plays the look). The shared looks are on the left; "Show the looks of compositions" additionally shows the
  compositions' own looks (recorded or generated steps), "◆ Make shared" makes one of them shared.
- **Compositions:** steps that play looks. Editor with default times (fade, hold), play mode (loop, ping-pong, once),
  intensity (sets / scales the light below), step list (pick a look, own times with the default time as placeholder,
  load/update the look, repeat, reorder), "⏺ Record step", add shared looks and "✦ Generate from an effect…" (chase,
  color change, rainbow, wave, flicker). Plus live preview, "Show on stage" and "In scenes …". While a composition runs
  via "Show on stage", it carries "ON STAGE" in the list on the left. The steps stand in **rows**: steps of a row run in
  parallel and stand next to each other, fade/hold apply to the whole row. "Start" per step: "Begin" (first row),
  "with …" (parallel to a row) or "after …" (new row after it); "⏺ ∥" records the programmer as a parallel step into
  a row. See [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md#parallel-steps).
- **Moves:** figures and moves for pan/tilt fixtures, see [MOVEMENT.md](MOVEMENT.md).
- **Party:** editor of the party matrix (see [PARTY-MODE.md](PARTY-MODE.md)): rows and columns (up to 12 each), click a
  field and set it up as text, scene or composition, label, fill and border color, toggle/flash and group; a click
  between two rows draws a divider.
- **DMX:** monitor of all 512 channels of a universe, live.
- **Scenes:** edit the scene list (number, name, cue note, auto-follow; sorted by number) and record scenes. Reorder by
  drag & drop at the handle ⋮⋮ (or Alt+↑/↓): the order stays the number; the dragged scene gets a free number between
  its new neighbours (between 2 and 3 → 2.5, otherwise the next whole number); the others keep their numbers. "+ Empty
  scene" inserts after the selected scene (also with a number in between), without a selection at the end. Every
  scene belongs to the cue list. Below, "Faders" assigns a composition to up to 8 faders (e.g. house lights), name
  optional. The list takes 30 % of the width, the editor 70 %; "Save" sits at the bottom of the editor and stays
  visible while scrolling.
  The **script** is edited with formatting: speaker names in their show-mode color, stage directions purple, light
  markers orange, pauses green, control characters (`**`, `:`) dimmed. Plain text is what is saved; a toolbar sets the
  speaker of the current line, turns the selection into a stage direction and inserts a light marker
  (`//## Name (info)##`) or a pause (`//|| Pause (info)||`). "👁 Preview" shows the text as in show mode.
  In the editor: section **Compositions** (add existing, "⏺ From programmer", "✦ Effect"; per use level, tempo and
  "Restart at GO"; reorder; a click on the name opens the composition – changes apply in all scenes) and **Keys** 1–8
  (composition, toggle/flash, fade). A new scene takes over the keys of the scene before it.
  "⧉ Duplicate" copies the scene directly after it with the same compositions and keys. With the fade times there is
  also the curve (linear, s-curve, square law; empty = the project's). Each light frame (the start of the scene and
  every part after a light marker) has its own "Fade in" and "Fade out" – for the start those of the GO into the
  scene (empty = the project's), for a marker those of the change at the marker (empty = the scene's) – and its own
  "▶ Show on stage": it plays that light on the real stage (above the
  playback, below the programmer); "■ Stop showing" or "■ Stop" in the header ends it. Scenes are changed through
  their compositions, not from the programmer – the looks of a composition are updated in the composition.
- **Settings:** setup PIN, network access for tablet/phone (with addresses), list of DB backups and "Back up now". The
  global fade times are set with the project. Later keyboard shortcuts and MIDI mapping with MIDI learn
  ([MIDI.md](MIDI.md)).

### Programmer (right)
The fixtures are in the middle as a **list** (glowing dot, full name – long names wrap –, fixture type and mode,
address, level, the heads for multi-pixel fixtures). The panel on the right gets most of the width (groups : list :
panel ≈ 200 px : 2 : 3); its sections stand next to each other in columns as soon as there is room.
It is built **dynamically from the fixture type**, so a new YAML is enough for new fixtures:
- RGB/RGBW → **color wheel** + presets + white share · `color-wheel` → **color buttons** from the YAML `ranges`
- `pan`/`tilt` → **XY pad** · `strobe`/`shutter` → segmented fader · `generic` → fader with name
- Multi-selection (group, Shift-click, lasso): changes apply to all selected fixtures
- **Presets** at the top of the panel, per group color / beam: tapping applies the preset to the selection, "+"
  records the values of the selection as a new preset, "✎ Edit" to rename, update (from the programmer) and delete.
  A look remembers which preset a fixture uses – when the preset is updated, all looks change with it. A value set by
  hand detaches the fixture from the preset for that group. The look view shows the presets per fixture.
- **Heads** for multi-pixel fixtures: "All" or a single head; all controls then act on that head; the fixture card
  shows the heads as a small bar
- "Raw channels" shows address + DMX value of every channel

### Building scenes
1. Select a scene or create a new one
2. **Preview:** the scene is loaded into the programmer and is visible live
3. Adjust in the programmer
4. **Record** (new scene) or **Update**: "Replace" replaces the whole picture, "Merge" only takes what the programmer
   sets – optionally only certain attribute groups (intensity, color, position, beam, other) and/or only the fixtures
   selected in the programmer. The previous picture stays in the history
5. Optional **trying a cross-fade** ("⇢ Fade in" under each light frame of the scene editor): fades on stage from the
   light that is there right now – whatever the stage preview shows (a scene, a part, a composition, a move, a fade
   still running) or the dark stage – into this light. For the start of the scene with the real fade times of the GO
   incl. delay, for a part after a light marker with the times of passing the marker. What is on stage and in the new
   light keeps running and does not move. Typical: "▶ Show on stage" for scene 1, then "⇢ Fade in" at scene 2.
   Progress under the frame; afterwards the light stays until the preview is stopped. "▶ Show on stage" switches
   without a fade
6. **Printable cue list** ("🖨 Cue list"): a page of its own without the app frame, also as PDF via the print dialog

---

## Mode 2: Show

Optimized for one task: press GO at the right time, without mistakes.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ● SHOW   "The Tempest"                   Art-Net ● live      21:14:03   🔒 Setup │
├──────────────────────────────────────────────┬─────────────────────────────────┤
│ SCENES                                       │  NOW                            │
│   1    Audience entry                ✓       │  ┃ 4  Storm rising              │
│   2    Act 1 – Dawn                  ✓       │  ┃ ████████████▒▒▒▒  fade-in 2.1s │
│   3    On the beach                  ✓       │                                 │
│ ▶ 4    Storm rising             ◀ NOW        │  FADING OUT                     │
│   5    Lightning                ◀ NEXT       │    3  On the beach ▒▒▒░░░ 1.4s  │
│   6    After the storm                       │                                 │
│   6.5  Prospero alone                        │  NEXT                           │
│   7    Finale                                │  ┃ 5  Lightning                 │
│                                              │  ┃ "GO on: thunderclap (sound)" │
│                                              │                                 │
│                                              │  ┌───────────────────────────┐  │
│                                              │  │           G O             │  │
│                                              │  │         (space)           │  │
│                                              │  └───────────────────────────┘  │
│                                              │  [◀ Back] [⏸ Pause] [⏭ Finish]  │
├──────────────────────────────────────────────┴─────────────────────────────────┤
│ MASTER ▮▮▮▮▮▮▮▮▮▮▯ 92 %                                           [ BLACKOUT ]  │
└────────────────────────────────────────────────────────────────────────────────┘
```

- **Scene list** on the left: finished scenes dimmed, the current one highlighted, the next one marked. Tapping picks
  a scene as the *next* one (jump); it is only triggered with GO.
- **Progress as a stack** on the right: the scene before (small, indented; during the fade "Fading out" with a bar),
  **Now** large, **Next** a bit smaller, "Then" (the one after) smaller still and "+ n more scenes"; a line with dots
  connects them. If the scene has light markers, the "Now" box lists its parts (start and every marker with info
  text), the running one is highlighted. A marker change only fades within the scene: a small bar below the marker
  shows the progress, the stack and the large bar stay reserved for the scene change (`fade.withinScene` in the
  playback status).
- **Cue note** of the next scene clearly visible (the cue for GO).
- **GO button** huge; on touch it reacts at once (pointerdown). Double-GO protection through a short lock time
  (configurable, e.g. 300 ms).
- Small **color preview** per scene in the list (gradient of the contained colors).
- **Auto-follow:** scenes with a stay time carry a ⟳ symbol in the list. During the stay time "Now" shows a countdown
  ("next in 6 s"). A tap on GO triggers at once.
- After a restart in show mode the show continues at once at the scene that was on stage; after a restart in setup
  mode a banner asks "Resume the show at scene 4?"
- No programmer, no patch, no scene editing. Only master and blackout.

### Keyboard (show)
`Space` GO · `←` back · `P` pause · `Enter` finish fade · `B` blackout · `↑/↓` previous/next speech in the script ·
`Shift`+`↑/↓` choose the next scene · `1`–`8` extra keys (flash: while pressed)

### Show time
At the top of the show view (next to the project name) and on the show remote: ⏱ how long the show has been running
since the GO into the first scene, as `12:05` or `1:02:05`. Before the show `–:––`; at the end of the show it stands
still (grey). ■ Stop resets it. See [PLAYBACK.md](PLAYBACK.md#controls-in-show-mode).

### Stopping the show
"■ Stop" next to Back, Pause and "Finish fade" aborts the show: scene, keys and faders go to 0 at once without a
cross-fade, and the show stands before scene 1 again (the next GO starts from the beginning, there is nothing to
resume). Against accidental triggering, the first click asks "Really stop?"; only a second click within 3 s stops.
Unlike blackout, the position is not kept. `POST /api/playback/stop`.

### Script in the show
The tab **Script** next to Scenes, Stage and DMX shows the script of the scene on stage. `↑`/`↓` (on a tablet ▲/▼)
move from speech to speech, the current one is highlighted and kept in the middle; stage directions are skipped, a
pause (`//|| … ||`, shown as "⏸ …") is a stop of its own where nobody speaks. The
position is server state, so all browsers show the same speech. Passing a light marker fades to the light of that
part of the scene.

### Stage in the show
The tab **Stage** next to Scenes, Script and DMX shows the stage view read-only: the fixtures glow like the real
output; moving, changing shapes and the keyboard shortcuts of the stage view are not available here.

### View only (`#/view`)

For phone or tablet: the link **👁 View** in the header (or directly `http://<computer>:8080/#/view`) opens only the
stage, glowing live like the output, with project, mode, the scene on stage and the active light marker above it, plus
a hint on blackout. There are no controls and no keyboard shortcuts; the page does not follow the operating mode and
does not ask for the PIN. ✕ leads back into the app.

### Follow the script, view only (`#/view/script`)

For tablet or phone, e.g. for the prompter, stage management or the director: the link **📜 Script** in the header
(or `http://<computer>:8080/#/view/script`, also via "📜 Script" from `#/view`) shows only the script of the scene on
stage. The speech the show is at is highlighted and kept in the middle – the page follows the script position of the
server (↑/↓ in show mode) but cannot change it. Above it scene, active light marker, blackout, and the next scene at
the end of the text. Text size via A−/A+ (remembered by the browser). Scrolling by hand (e.g. to read ahead) pauses
following; "↧ Follow the show" or the next scene resumes it. The screen stays on. Like `#/view`: no controls, no
keyboard shortcuts, no PIN, does not follow the operating mode.

### Cue list on a phone, view only (`#/view/cues`)

For a phone (e.g. an iPhone backstage): the link **🎬 Cues** in the header (or `http://<computer>:8080/#/view/cues`)
shows only the show time, large, the scene on stage with its active light marker and cue note, and the next three
scenes with their cue notes – starting at the next scene the operator chose, then on in the cue list; "End of the
show" when the show ends after them. The screen stays on. Like `#/view`: no controls, no keyboard shortcuts, no PIN,
does not follow the operating mode.

### Show remote for a tablet (`#/remote`)

For running the show from a tablet (e.g. an iPad in landscape) next to or instead of the laptop: the link
**🎛 Remote** in the header (or `http://<computer>:8080/#/remote`) shows only what the show needs – now (with the
fade or auto-follow bar) and next (with its cue note, ▲/▼ choose another next scene), GO, Back, Pause,
Finish fade and Stop (asks first, like in the show view), the whole script of the scene on stage with ▲/▼ through its
speeches (like `↑`/`↓` in the show view: the current speech is highlighted and kept in the middle, "3 / 12" counts
them; passing a light marker fades to its light), the keys of the scene, the faders, master and blackout. No scene
list, stage or DMX.

Layout in landscape: the text comes first, so it can be read from a distance. A narrow column on the left holds
now, next, a compact GO, the playback buttons, the 8 keys and the faders (if the project has any); the script takes
the rest of the screen (the current speech at its top, what comes next below), with A−/A+ for its text size
(remembered by the browser), ▲ back and a ▼ (next speech) across its bottom, the button pressed most during a play.
Master below. Long names are cut, cue notes after three lines; the script scrolls inside its panel, the panel stays
(empty) for a scene without text, and the "show mode is off" hint lies over the panels – so the page does not jump.
On a short screen the left column scrolls. In portrait one column: now, next, GO, buttons, the script down to master
(▼ in reach without scrolling), keys and faders below.

The remote wears the show colors (amber) and shows the show time (⏱) in its header. It only runs the show: outside
show mode it says so and GO, the playback buttons and the keys are disabled;
it cannot start show mode itself. Like the show view it needs no PIN; like the view-only pages it does not follow the
operating mode, so the tablet stays on the remote. The screen stays on. ✕ leads to the show view. Tablets need
network access (Settings).

### DMX in the show
On the left you can switch between **Scenes** (scene list) and **DMX**: the DMX monitor shows only the used channels,
grouped by fixture and all universes one below the other, live, while GO, Back, Pause and the extra keys on the right
stay usable (also by keyboard). The browser remembers the choice.

### Faders (show)
Below the extra keys are the assigned faders (e.g. house lights) with slider and "0"/"Full". They act at once,
independent of GO; on a phone they are hidden. See [PLAYBACK.md](PLAYBACK.md#faders-in-the-show-eg-house-lights).

### Extra keys (show)
Below GO is the key bar with the keys of the current scene (name of the composition, on/off). They glow along while
fading in and out. All 8 places are always there (unassigned ones dashed), so that GO and the keys never jump. On a
phone the bar is hidden.

---

## Technology
- Svelte 5 + TypeScript + Vite, styling with CSS variables (design tokens), no heavy UI framework.
- Color wheel, fader and XY pad as components of their own (pointer events for touch and mouse).
- Central store fed by WebSocket updates. Progress bars are interpolated on the client (the server sends start time +
  duration), so that they run smoothly.
- The screen stays on in the show view (`lib/wakeLock.svelte.ts`): Wake Lock API where the browser allows it (HTTPS or
  localhost), otherwise NoSleep.js with an invisible video – that also works on tablets over HTTP, but only starts
  there after a tap (hint "Tap to keep the screen on"). Later a full-screen PWA.
- The computer itself does not go to sleep while Lumen runs via `run.sh` (`caffeinate` on macOS).
