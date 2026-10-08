# Changelog

What changed in Lumen, newest first. The version of the Mac app is `1.0.<number of commits>`, so each version range
below covers the commits of that day. Shown in Lumen under Settings → What's new.

## 1.0.81 – 1.0.83 · 2026-10-08

### Added
- Formatted script editor in the scene editor: speakers in their show colors, stage directions and light markers
  marked. A toolbar sets the speaker of a line, turns a selection into a stage direction and inserts a light marker.
- Version and changelog in Settings ("What's new").

### Changed
- Show mode: a light marker changes the light within the scene box. The box lists the parts of the scene, a small bar
  under the marker shows the fade; the stack on the right and the big bar only move for scene changes.

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
