# Changelog

What changed in Lumen, newest first. The version of the Mac app is `1.0.<number of commits>`, so each version range
below covers the commits of that day. Shown in Lumen under Settings → What's new.

## 1.0.102 · 2026-10-12

### Added
- Quick setup in the patch: tick "Quick setup" while adding fixtures (or use "Quick setup…" for fixtures already
  patched) and Lumen makes intensity looks (0 / 50 / 100 %), color looks (red, orange, green, blue, violet), moves
  (line, circle, eight) for moving heads, a composition for each look, and a party row with a button for each – one
  button switches all the fixtures together. Optional, everything can be changed afterwards.
- Quick setup also works for moving heads with a color wheel or CMY: each color uses the closest wheel slot (from the
  slot colors in the fixture profile) or cyan/magenta/yellow. The colors become a palette of presets the looks refer
  to – change "Red" for the spot once and every look follows; the other wheel slots (e.g. yellow, white) are added to
  the palette as well.
- Party gradients: a party field can span several columns and show an intensity or color gradient; touch or drag along
  it to pick the brightness or color at once. Quick setup adds a row of them under the buttons.
- Several fixtures at once in the patch: shift-click (or cmd-click) fixtures in the channel grid, or tick them in the
  new list view (shift-click ticks a range; the choice of view is remembered on the device). For fixtures of one type
  the side panel offers quick setup for all of them together, plus "Locate all".

### Fixed
- Password managers such as 1Password no longer put their icon into Lumen's fields (e.g. a fixture's name) or open
  their menu while typing – Lumen has nothing for them to fill in.
- Party fields in a wide matrix keep words whole and shorten long group names instead of wrapping them.
- The header fits again: since the Remote link it ran off the right edge in setup mode on every screen below
  about 2000 px (mode switch cut off). The setup tabs now get a row of their own on screens narrower than 1900 px.

## 1.0.98 – 1.0.99 · 2026-10-11

### Added
- Show remote for a tablet (link "🎛 Remote" in the header, or `#/remote`): now and next, a big GO, Back, Pause,
  Finish fade, Stop, the keys of the scene, faders, master and blackout – nothing else. Choose the next scene with
  ▲/▼; the whole script of the scene with a big ▼ to the next speech (highlighted and kept in the middle). Works in
  show mode only, needs no PIN, the screen stays on; landscape and portrait.
- Show time: ⏱ how long the show has been running since the GO into the first scene, in the show view and on the
  remote. It stands still at the end of the show, ■ Stop resets it.

### Changed
- The Mac app turns off App Nap, so macOS cannot slow down the light output while Lumen is in the background.
- A server that restarts in show mode continues the show at once at the scene that was on stage, without waiting for
  "Resume". Lumen no longer sends a dark frame first, so Art-Net nodes that hold their last values stay lit through
  the restart. In setup mode the scene is still only offered.

## 1.0.95 – 1.0.96 · 2026-10-10

### Added
- View-only script (link "📜 Script" in the header, or `#/view/script`): follow the text of the scene on stage on a
  tablet or phone. The speech the operator is at is highlighted and kept in the middle; text size A− / A+, scrolling
  by hand pauses following until "Follow the show" or the next scene. No controls, no PIN, the screen stays on.

### Changed
- The view-only stage and the view-only script link to each other.

## 1.0.81 – 1.0.94 · 2026-10-08

### Added
- Moves for moving heads (new Setup tab "Moves"): draw figures in the pan/tilt grid (templates: circle, eight, lines,
  triangle, rectangle, random), lay them onto heads with size, shift, rotation, tempo, loop / once / ping-pong and an
  offset between the heads; each head can be shifted, mirrored and reversed. Preview in the browser and "Show on
  stage". A figure with one point is a fixed position.
- Scenes, light markers, keys and party fields play moves next to looks and compositions. A head without a move in
  the next scene stays where it is.
- Programmer: "Record position" turns where the selected heads point into a move; "Record scene" can put the position
  into the new scene as well.
- Move in dark per scene: heads that point somewhere else dim out, move while dark and dim in at their new place.
  Dim-out and travel times per project and scene.
- Pan/tilt limits per fixture in the patch: the show never leaves them, the programmer can still reach every point.
  The XY pad and the move editor show them as a frame.
- Project files version 4 with figures and moves.
- Fixture profile: DELIBANG LED moving head wash 7 × 10 W RGBW (9 / 14 channels).
- View-only stage for phones (`#/view`, "👁 View" in the top bar): the stage glowing live with the scene on
  stage, nothing to control.
- Show mode: "Stop" ends the show at once – scene, keys and faders to 0 without a fade, the next GO starts from the
  beginning. A second click confirms it.
- Show mode: a "Stage" tab next to Scenes, Script and DMX shows the stage view read-only, glowing like the output.
- Formatted script editor in the scene editor: speakers in their show colors, stage directions and light markers
  marked. A toolbar sets the speaker of a line, turns a selection into a stage direction and inserts a light marker.
- Version and changelog in Settings ("What's new").

### Changed
- Looks and presets no longer hold pan and tilt – positions come only from moves. Older project files lose them on
  import (with a warning in the log); position presets are gone.
- Scene editor: with light markers, the settings of each part (start and every marker) are framed.
- Show mode: a light marker changes the light within the scene box. The box lists the parts of the scene, a small bar
  under the marker shows the fade; the stack on the right and the big bar only move for scene changes.

### Fixed
- A fixture lit before and after a scene change or light marker dimmed down first (with a delay or a short fade-out
  almost to dark) and then up again. It now fades straight to its new level.
- Light markers waited for the delay of the scene; the delay is for the GO into the scene only.
- Scenes view: with many scenes the whole page scrolled, heading and editor too. Now only the list scrolls.

## 1.0.68 – 1.0.80 · 2026-10-06

### Added
- Three levels: looks (fixed values), compositions (steps playing looks) and scenes (compositions plus keys).
  Existing shows and project files are converted.
- Generators turn chase, color cycle, rainbow, wave and flicker into ordinary steps you can edit.
- Parallel steps: steps of a composition can start together and are shown side by side in a row.
- Light markers in the script (`//## Name (info)##`): every part of a scene gets light of its own, faded in when the
  script passes the marker. What is in both parts does not move.
- Looks can be used directly in scenes and their parts, next to compositions.
- Script: speakers ("Name: text") are marked and their speech indented; stage directions stand on lines of their own.
- Script preview in the scene editor, laid out as in show mode.
- Party mode as a matrix of fields: each switches a scene or composition on and off or shows a text, with label,
  fill and border color, toggle or flash, and groups in which only one field is on. Lines can separate the rows.
- Stage view: the programmer controls of the selected fixture as an overlay.
- Stage view: fixtures drawn as spot, LED bar or LED strip, with rotation and length.
- "Clear all" in the Programmer and Stage tabs resets every programmer value.
- The composition list marks the composition playing via "Show on stage".
- Show mode: the scene before, now, next and the one after as a stack; the eight key slots keep their place; the DMX
  tab shows only patched channels of all universes.

### Changed
- Faders play a composition instead of a scene; scenes outside the cue list are gone (faders take the first
  composition of their scene).
- Programmer: fixtures as a list instead of tiles; the controls take most of the width and use columns.
- Scenes view: the editor takes 70 % of the width, Save stays at the bottom.
- Show mode: up and down step from speech to speech instead of sentence by sentence.
- Dimmer packs without a master: the programmer shows "All channels" and each channel; no hidden master value is
  stored.
- lightmaXX LED Blinder 2: the two lamps are heads with a dimmer each.
- Project files are now version 3; versions 1 and 2 can still be imported.

### Fixed
- A look that sets only one head (e.g. channel 1 of a dimmer pack) played on the whole fixture.
- Deleting a fixture or a composition showed an error although it worked.
- The head chosen in the programmer stuck when another fixture was selected.

### Removed
- The first party mode: tap tempo, color palettes, group intensity and party buttons.

## 1.0.44 – 1.0.67 · 2026-10-05

### Added
- Fade times per scene: fade-in, fade-out and delay.
- Fade curves: linear, S-curve or square law, per project and scene.
- Rehearse the fade into a scene in setup mode.
- Printable cue list for the operator.
- Script in show mode: step through the text of each scene, all browsers follow.
- Duplicate a scene with its look, compositions and keys.
- Merge parts of the programmer into a scene; history with undo for scene looks.
- Presets for color, position and beam; scenes refer to them and change with them.
- Faders in show mode, e.g. house lights.
- Multi-pixel fixtures: heads in profiles, per-head values in programmer, output and compositions.
- Import fixture profiles from the Open Fixture Library.
- Network interface per universe; missing Art-Net nodes show up during the show.
- Mac app: Lumen.app in a DMG; shipped fixture profiles are updated unless you changed them.
- Party mode, first experimental step.

### Changed
- Sturdier show: reloads off the render lock, live connection watchdog, the screen stays on.
- Show view: only the script and cue list scroll, tabs and scene heading stay.
- Show and party mode only start from setup.

## 1.0.1 – 1.0.43 · 2026-10-02

### Added
- Fixture library in YAML with example profiles; projects and patch stored in SQLite.
- DMX engine with Art-Net output, discovery of nodes and unicast.
- Web UI: setup mode with projects, universes and patch; programmer with groups and live sync between browsers.
- Scenes, playback with GO, show mode, resume after a restart.
- Watchdog and health status of the light output.
- Project export and import, automatic database backups.
- Configuration PIN and network access for tablets and phones.
- Stage view with floor plan and live output; DMX monitor.
- Compositions: chases, color cycles, rainbows, waves and flicker running in scenes, with editor and preview.
- Extra keys in show mode; reorder scenes by drag & drop.
- `build.sh` and `run.sh`.
- Fixture profiles: LED moving head wash, Eurolite EDX-4 and ERX-4, Eurolite LED BAR-6 QCL, Stairville LED Flood
  Panel 150, lightmaXX LED Blinder 2, Rtktoup LED par, Robe Robin MiniMe.

### Fixed
- Gaps when patching several fixtures at once.
