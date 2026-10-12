# Roadmap – iterative steps

Every iteration ends with a **working state** and a clear **definition of done (DoD)**.
The order is chosen so that real light comes on as early as possible and a show is playable as early as possible.

Legend: ⬜ open · 🟨 in progress · ✅ done · 🧪 experimental

---

## ✅ Iteration 0 – project skeleton
- Maven multi-module (see [ARCHITECTURE.md](ARCHITECTURE.md)), Java 26, Spring Boot 4.1.1 (via Spring Initializr)
- Created: `lumen-domain`, `lumen-application`, `lumen-app`; adapter modules follow in their iterations
- `lumen-app` starts, `/actuator/health` answers
- ArchUnit test: domain/application do not depend on Spring or adapters
- `.gitignore`, `git init`, `CLAUDE.md` with build commands

**DoD:** `mvn verify` green, app starts.

## ✅ Iteration 1 – fixture library (YAML)
- Domain: `FixtureType`, `Mode`, `ChannelDefinition`, `ChannelFunction`, `FixtureTypeValidator`
- Adapter `library-yaml`: loads `fixture-types/` (incl. subfolders), unknown fields are reported as errors
- Adapter `web`: first REST controller
- Example profiles in the repo (PAR, dimmer, moving head)
- `GET /api/library/fixture-types`, `GET /api/library/fixture-types/{id}`, `GET /api/library/issues`

**DoD:** example profiles are loaded, a broken YAML produces understandable issues without preventing the start.

## ✅ Iteration 2 – projects & patch in SQLite
- Adapter `sqlite`: sqlite-jdbc, Flyway `V1__projects_and_patch.sql` (see [PROJECT-DATA.md](PROJECT-DATA.md)), JdbcClient
- Domain: `Project`, `Universe`, `PatchedFixture`, `Group` + patch validation
- REST: projects (CRUD, activate), universes, fixtures, groups, patch issues under `/api/projects/{id}/…`
- Tests: repository tests against a temporary SQLite file

**DoD:** create a project via REST and patch it with fixtures; data survives a restart, overlaps are reported.

## ✅ Iteration 3 – DMX engine & Art-Net 💡
- Code language switched to English (docs stayed German until 2026-10-10)
- Domain: `Attribute`, `FixtureValues`, `DmxFrame`, `DmxRenderer` (8/16 bit, defaults, virtual dimmer, invert,
  grand master)
- Render loop with `OutputLayer`/`LayerMerger` (basis for playback and the later party mode)
- `Programmer` as the first layer (live values)
- Adapter `artnet`: build and send ArtDmx, sequence numbers, unicast/broadcast
- Test: byte-exact comparison of the ArtDmx packet
- `PATCH /api/programmer/fixtures/{id}`, `GET /api/output/universes/{n}/dmx`
- Patch changes and project changes are taken into the output at once

**DoD:** a real lantern is switched on via `curl` (or is visible in QLC+/Wireshark).
Checked without hardware: the real app sends ~40 ArtDmx packets/s with a correct header and the set channel values.

## ✅ Iteration 4 – UI skeleton & setup mode
- Module `lumen-frontend` (Svelte + Vite + TS), built into the JAR via `frontend-maven-plugin`
- Design tokens, dark theme, app shell with mode switch (show mode a placeholder for now)
- Project selection, universe management, patch view with fixture library and address grid
- In addition: a "Locate" button per fixture (lights up brightly to find it on stage), multi-patch with count and
  automatic next free address, clickable patch problems, server status in the header
- Stage and Scenes are set up as "Coming soon", show mode as a placeholder

**DoD:** a complete project can be created and patched in the browser.

## ✅ Iteration 5 – programmer & live sync
- WebSocket `/ws`, `StateNotifier`, throttling, reconnect
- Dynamic programmer (color wheel, color buttons, XY pad, range faders, raw channels)
- Groups, multi-selection, grand master, blackout
- Fixture cards with glow

- Implemented as a tab of its own, **Programmer**: groups on the left, glowing fixture cards (color/brightness like
  the real light, incl. grand master), dynamic panel on the right; master bar with blackout (key `B`) at the bottom
- WebSocket: the server bundles changes (max. ~30/s), the client sends fader movements throttled, fallback to REST

**DoD:** light can be controlled freely in the browser, a second browser shows all changes live.

## ✅ Iteration 6 – programming scenes
- Domain: `Scene`, `AttributeValues`; repository for scenes
- Scene list: create, numbers like "2.5", name, cue note, optional auto-follow time; the order follows from the number
  (instead of drag & drop – otherwise scene 5 could come before scene 3)
- "Record scene" directly in the programmer, color preview per scene in the list
- Along the way: cache headers for the web UI (otherwise the browser shows the old interface after an update)
- Record / update from the programmer, load a scene into the programmer (preview)
- Settings: **global fade-in/fade-out time**

**DoD:** a show with 5 scenes is programmed and completely there after a restart.

## ✅ Iteration 6b – Art-Net discovery & unicast 📡
Reason: real hardware (own ESP8266 dimmer, 1 channel, on Wi-Fi). Broadcast sends the packets of **all** universes to
**all** devices and is unreliable over Wi-Fi (no acknowledgement, slowest radio rate).
- Adapter `artnet`: send **ArtPoll** periodically (e.g. every 3 s), evaluate **ArtPollReply**: IP, name, reported
  port addresses; mark nodes without a reply after ~10 s as offline
- New output mode per universe: **auto (discovery)** – packets via unicast to all nodes that report this universe
- Several **manual target IPs** per universe; broadcast stays as an option
- No migration needed: `universe.target` stores `auto`, `auto,<ips>`, `<ips>` or `broadcast`; existing values stay valid
- UI: list of found nodes (name, IP, universes, online/offline), assignment in the universe dialog
- Hint in the UI when a universe goes to no node or a node reports a universe that does not exist in the project

**DoD:** the ESP dimmer is found automatically and gets its packets via unicast without an IP being entered.
Checked: the real ESP (192.168.0.150, port address 0) was found via ArtPoll; unicast to found nodes is tested end to
end with a simulated ESP (same ArtPollReply format as the firmware).

## ✅ Iteration 7 – playback engine & show mode 🎭
- Domain: `Playback`, `Crossfade` with snapshot, HTP/LTP mixing, pause, injected `Clock`
  (extensive unit tests for the fade behaviour, see [PLAYBACK.md](PLAYBACK.md))
- GO / back / jump / pause / finish fade, start and end state (blackout before scene 1, "end" after the last)
- Auto-follow: automatic GO after the stay time, countdown in the UI
- Show mode UI: scene list, now/fading out/next with progress, cue note, large GO button, keyboard shortcuts, lock
  against editing, wake lock
- Show mode is server state: locks the programmer (409), all connected clients follow
- Still open (→ later): rehearsal cross-fade in setup mode and fade times per scene → both implemented in iteration 10

**DoD:** a complete show can be played through with the space bar, fade-out and fade-in run in parallel, a double GO
does not cause a jump.

## ✅ Iteration 8 – theatre readiness / robustness 🛡️
- Persist `playback_state`, resume after a restart: banner "Resume the show?" in show mode, the scene comes back
  without a fade. The operating mode also survives a restart (`app_setting`), Flyway `V3`
- Project export/import as `.lumen.json` (import always creates a new project), automatic DB backups via
  `VACUUM INTO` at start, on project change and when show mode starts, the newest 30 are kept
- The render loop also catches `Error`s, a watchdog replaces a hanging render thread; health status (render rate,
  Art-Net packets/s, send errors) every second via WebSocket, shown in the header
- Setup PIN (PBKDF2 hash in `app_setting`, lock after 5 failed attempts), starting show mode locks all browsers.
  Network access via a setting (takes effect after a restart). Implemented without Spring Security
  (session + `HandlerInterceptor`)
- New tab **Settings**: PIN, network access, backups
- Soak test `ShowSoakTest`: 3 h show simulation (432,000 frames, simulated clock) in about 5 s, the heap stays constant

**DoD:** hard-kill the server during scene 4 and restart it: after confirmation the light is in scene 4 again.
Checked with `ResumeAfterRestartTest`: two app starts on the same database. After the restart the server is in show
mode again, the light is dark, and after "Resume" scene 4 is on the DMX output again.

## ✅ Iteration 9 – stage view & comfort 🎪
- Tab **Stage**: drag fixtures onto the stage by drag & drop (pointer events, also by touch), dragging them off the
  stage removes them, "Place all" distributes the rest. Optional floor plan (PNG/JPEG/WebP up to 5 MB, table
  `stage_plan`, Flyway `V4`, travels with the project export). Fixtures glow with the **real output** (programmer +
  playback + master); light cone as a light spot, offset roughly by pan/tilt for moving heads
- Live output via WebSocket subscription (`output`, `dmx:<n>`), at most 10×/s and only on changes
- Color preview per scene now also in the scene list of show mode
- Hot reload of the fixture library: polling every 2 s instead of `WatchService` (which only polls about every 10 s on
  macOS anyway); loading happens once the files have been unchanged for one round; additionally
  `POST /api/library/reload`
- Tab **DMX**: monitor with a 512 grid per universe, channel mapping (fixture + channel name), percent display
- Responsive: a tablet in portrait shows status and GO at the top in show mode, the list below; on a phone only
  now/next, GO and back (no master/blackout, no pause)

**DoD:** the show can be set up and played comfortably on a tablet.
Checked in the browser (desktop, tablet portrait 820 px, phone 390 px): setting up the stage by dragging, live glow on
scene change, DMX monitor, show control. Not yet tried on a real tablet.

## ✅ Iteration K – compositions & dynamic scenes 🌈
The concept was merged into [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md) with iteration S (the old
document is in the Git history). Scenes then consist of a **static base** (as before) plus any number of
**compositions**: sequences like a chase, a color change on RGB PARs or free step sequences that run in the scene on
top of the fixed light. Compositions are maintained in a library and **copied** when inserted into a scene. The copy
can be adjusted freely there.

**✅ K1 – engine & scene integration**
- Domain: `Composition` with templates as pure functions: **chase**, **color change**, **rainbow**, **wave/pulse**,
  **flicker**; target = group (dynamic) or fixture list in a fixed order, spread
- **Step sequence (chaser):** steps with values per fixture, hold/fade time per step, loop/ping-pong
- Mixing rules base ⊕ compositions (intensity absolute/HTP or relative, color LTP with level, later one wins)
- `Playback`/`Crossfade` with time-dependent target values: compositions keep running during the fade-out, a copy with
  the same origin continues seamlessly in the following scene ("restart" per use), pause freezes them
- Flyway migration `composition` (library, scene copies, extra keys), `composition_target`, `composition_step`,
  `composition_step_value`; REST for library, steps and copies in scenes
- Implemented: all five templates and step sequences as pure functions with table tests, `CompositionMixer`, playback
  with continuing during the fade-out and a seamless transition for the same origin (`V5`). Compositions travel with
  the project export. The soak test now plays scenes with a chase, a color change and continuous flicker (about 50 µs
  per frame, heap constant). No UI yet: K1 can only be used via the REST API so far

**✅ K2 – editor & preview**
- New tab "Compositions" (library): choose a template, target and order, parameters, animated preview
- Step sequence editor: record/update steps from the programmer, times, reordering
- Live preview on stage as an output layer of its own (locked in show mode)
- Scene editor: insert from the library (copy), adjust, order, level, tempo factor, "restart", "reload from library" /
  "save to library"; hints on overlaps and unsuitable fixtures
- Scene list: icon and animated color preview for dynamic scenes
- Implemented: tab "Compositions" with an editor for all types (saves automatically). The server computes the preview
  in the editor and sends it via WebSocket (`preview:<id>`), the stage stays untouched; "Show on stage" puts the
  composition as a layer of its own (priority 75) above the playback. Scene editor with a section "Compositions",
  hints on overlaps, unsuitable fixtures and fast flashing (⚡, from about 3 Hz). Deviation: the scene lists show type
  icons instead of an animated color preview

**✅ K3 – extra keys & extensions**
- **Extra keys in show mode:** switch compositions on independently of the scene (toggle/flash, keys `1`–`8`, later
  MIDI), an output layer of its own above the playback
- **One-off sequences** (run through once and stop at the end, e.g. a sunrise)
- Multi-pixel cells as targets, marking of fast flicker/strobe effects
- Implemented: 8 extra keys per project (toggle/flash, fade time, own copy of the composition), **enabled per scene**
  (new key: in all scenes, new scene: inherits the keys of the scene before), go off in a scene without the key and at
  the "end" (Flyway `V6`). Key bar and keys `1`–`8` in show mode, without PIN. "Run once" for all types with an end
  (not flicker / random chase). Marking of fast effects (⚡) since K2. **Postponed:** multi-pixel cells – the fixture
  library does not know multi-pixel fixtures yet (→ implemented in iteration G: "Each head separately")

**DoD:** a scene with warm white base light, a chase on a row of dimmers and a color change on the RGB PARs can be put
together in the browser. In show mode the sequences fade in and out softly on GO, continue in the following scene
without a jump, and pause stops them. An extra key can lay a thunderstorm over any scene.
Checked: putting together and preview in the browser (test server), continuing/pause/double GO in domain tests, in
show mode a "Thunderstorm" on key 1 laid over the warm white scene via keyboard and switched off again. Not yet tried
with real fixtures.

## ✅ Iteration T – script in show mode 📜
Reason: in show mode you should only have to follow one view, not script and lighting desk separately.
- Every scene has its **own text** (`scene.script`, Flyway `V10`, travels with the project export), entered in the
  scene editor. No running text across all scenes
- Split into sentences at `.`, `?`, `!` and `…`, but not in numbers ("3.5") and abbreviations ("e.g.", "Dr.",
  "U.S.A."); `**note**` is a stage direction: displayed differently and skipped by the arrow keys
- Show mode: new tab **Script** to the left of "Scenes" and "DMX", shows the text of the scene on stage. GO stays fixed
  on the right in its place
- `↑`/`↓` go to the previous/next sentence, the current sentence is highlighted and kept in the middle automatically.
  Choosing the next scene moves to `Shift+↑`/`Shift+↓`
- The text position is **server state** (part of the playback status): laptop and tablet show the same sentence. On a
  scene change it starts again at the first sentence

**DoD:** a scene with several paragraphs and notes can be stepped through sentence by sentence with the arrow keys in
show mode, a second browser follows, notes are skipped, the current sentence stays in view even with a long text.
Implemented: splitting as pure logic (`src/lib/script.ts`, Vitest); a line break also ends a sentence; after a
punctuation mark followed by a lower-case word ("I… I") it does not split. Text position `scriptLine` in the playback
status, set via `PUT /api/playback/script` or WebSocket `playback.script` (without PIN), not persisted: after a restart
the scene starts at the first sentence. On a tablet ▲/▼ buttons instead of arrow keys, on a phone the script is
hidden. Checked in the browser (test server): 24 sentences with stage directions, automatic scrolling to the end, GO
starts the next scene at the first sentence, a position set via the API appears in the UI at once.
- Addendum (2026-10-06): **speakers** – a name with a colon at the start of a line ("Maike Schmitz: Hello Rolf!",
  1–4 capitalized words) is marked in color (every person always the same color), their text is indented below it
  until the next person speaks or an empty line comes. Stage directions always stand on a line of their own, without
  indentation; if the same person keeps speaking afterwards, their text continues indented. The scene editor counts
  the speakers
- Addendum (2026-10-10): **view-only script** `#/view/script` for following the text on a tablet or phone, see
  [UI.md](UI.md#follow-the-script-view-only-viewscript)

## ✅ Iteration N – network interface per universe 🌐
Before, there was only a global `bind-address`/`broadcast-address`, and discovery listened on `0.0.0.0`.
- List the interfaces of the computer (name like `en0`, IPv4, broadcast), `GET /api/network/interfaces`
- Discovery sends ArtPoll to the broadcast address of **every** active interface and assigns found nodes to their
  interface (subnet of the sender IP, pure logic with unit tests)
- One interface per universe (Flyway, empty = all/automatic as before): applies to broadcast and auto discovery, sent
  via a socket bound to the interface
- UI: choice in the universe dialog, interface in the node list, hint when the chosen interface is missing

**DoD:** with Wi-Fi and a USB Ethernet adapter at the same time, the packets of a universe only go into the chosen
network.
Implemented, with one deviation: no socket of its own per interface. A universe with an interface sends broadcast to
the **directed broadcast** of that network (e.g. `2.255.255.255`) and in auto mode only to nodes in its subnet; the
operating system sends both via the matching interface by itself. Before, broadcast went to `255.255.255.255` and so,
on macOS, only via the main interface. Manual IPs stay unchanged. If the interface is missing (adapter unplugged), the
universe is not sent instead of going into a wrong network; the universe view warns. Interfaces are re-read at most
every 3 s (about 0.5 ms), `GET /api/artnet/networks`, Flyway `V11`, travels with the project export. Checked with unit
tests (subnet, broadcast, target choice, ArtPoll to all networks) and in the browser (test server); not yet tried
with two real networks and Art-Net.

## ✅ Iteration H – node monitoring in show mode 🩺 (builds on iteration N)
- **Store expected nodes per project** (Flyway), so that a node that is already missing before the start is noticed
- Node status per universe in the health status: online / offline / manual IP without ArtPollReply (not checkable)
- Header: "Nodes 3/3" next to the Art-Net display, yellow/red as soon as an expected node is missing, details on click;
  hint in show mode when a node drops out during the show

**DoD:** take the ESP dimmer off power during a show: after ~10 s at the latest the header shows it as missing.
Implemented: a node is expected as soon as it reports a universe of the active project via ArtPollReply (Flyway
`V12`, stored so that it is also missing after a restart); in addition the manual IPs of the universes, which stay
"not checkable" without an ArtPollReply. `NodeWatchService` checks every second with the health status (only with
Art-Net on); missing nodes are warnings ("Node … does not answer – universe 1 may stay dark"). Header "Nodes 1/1",
red with a missing node, click shows the list; "Forget" (`DELETE /api/artnet/expected-nodes/{ip}`) only in setup mode.
In show mode, additionally a hint when a node drops out. Along the way: hints appear at the top in show and party mode
instead of at the bottom, so that they do not cover GO, back and the keys.
Checked with unit tests and in the browser with a simulated node on 127.0.0.1 (Lumen bound to loopback only): after
switching it off, "Nodes 0/1" red after about 9 s and a hint in show mode; after switching it on, green again after
about 3 s. Not yet tried with the real ESP.

## ✅ Iteration D – Mac app 🍎
- `jpackage` (in the JDK) builds `Lumen.app` with a built-in Java runtime and a `.dmg` for installing: drag into
  "Applications", afterwards in Launchpad/Spotlight and in the Dock like any other app
- A small window of its own (Swing, own module e.g. `lumen-desktop`): status, "Open in browser", "Quit"
- Data under `~/Library/Application Support/Lumen/` (DB, backups, own profiles), bundled profiles in the app; prevent
  sleep as in `run.sh`
- Known: unsigned without an Apple developer ID (first start via right-click → Open), firewall prompt for UDP 6454,
  build only for the own architecture (Apple Silicon)

**DoD:** Lumen can be installed from the `.dmg`, started from the Dock and opens the interface with a button.
Implemented with `./package-mac.sh` → `dist/Lumen-<version>.dmg` (version `1.0.<number of commits>` or `--version`,
without tests – `--test` runs them first –, `--no-build` packages the existing JAR), about 96 MB with its own Java runtime, ad-hoc signed.
Deviation: no module `lumen-desktop` of its own – the app starts the same JAR with `-Dlumen.desktop=true`
(`DesktopLauncher` in `lumen-app`); without the option Lumen runs without a window as before. Window with status,
"Open in browser", "Open data folder", "Show log", "Quit"; closing only hides it (the Dock icon brings it back),
quitting (Quit, ⌘Q) asks. Bundled profiles are only copied if they are missing in the library – own changes stay, but
new versions of bundled profiles do not arrive automatically either. The icon is drawn from
`packaging/macos/MakeIcon.java` when packaging.
Checked: DMG built, app started from `/Applications`, server ready after 1.5 s, profiles and log in the right place.

## ✅ Iteration P – programming comfort ✍️
Reason: review of 2026-10-05 – in everyday rehearsals copying, undo and reusable colors/positions were missing.
- **Duplicate scene:** "⧉ Duplicate" copies a scene directly after itself (number in between) with picture, times,
  text, compositions (same origin, so they continue seamlessly) and enabled extra keys
- **Partial update ("merge"):** "Update from programmer" replaces the whole picture or only takes what the programmer
  sets – optionally only certain attribute groups (intensity, color, position, beam, other) and/or only the fixtures
  selected in the programmer (`AttributeGroup`, the selection is now shared between programmer and scene editor)
- **Scene history / undo:** before every update, merge and restore, the old picture is saved as a revision (the newest
  20 per scene, Flyway `V14`); "History" in the scene editor with color preview and "Restore", "↶ Undo" for the last
  one. A restore can itself be undone again
- **Presets with reference** (color, position, beam; called "Presets" in the UI because party mode already had
  "palettes"): record from the selection in the programmer and apply. Scenes and programmer remember the preset per
  fixture and attribute group (`PresetRefs`, Flyway `V15`); an update of the preset writes the new values into all
  scenes that use it. A value set by hand detaches the fixture from the preset; deleted presets leave the values in
  place. They travel with the project export
- Deviation: spec changes (name, times, text) and deleted scenes are not in the history – the DB backups still cover
  those

**DoD:** duplicate a scene, take only the colors from the programmer, get an accidental update back with undo; change a
preset "Warm" and all scenes with "Warm" change along.
Checked with domain, repository and API tests (among them `PresetApiTest`: the update of the preset arrives in the
scene, export/import keeps the references). In the browser only looked at (preset bar and dialog in the programmer),
not clicked through – another Chrome extension blocked the automation. The UI is checked with `svelte-check`/Vitest.

## ✅ Iteration F – faders & fade curves 🎚️
- **Fade curves** per project and optionally per scene: linear, s-curve (also for color/position), square ("square
  law", softer in the dark), see [PLAYBACK.md](PLAYBACK.md#fade-curves). Flyway `V13`, travels with the export,
  choice in the project dialog, global fade times and scene editor; the print view names the project curve
- **Faders in the show** (e.g. house lights): up to 8 per project, each assigned a scene (Flyway `V16`), level via
  WebSocket `fader.set` or `PUT /api/playback/faders/{slot}` without PIN, in setup and show mode. Mixing like extra
  keys with a scene (HTP), above playback and extra keys; the level is kept while editing, not across a restart. See
  [PLAYBACK.md](PLAYBACK.md#faders-in-the-show-eg-house-lights)

**DoD:** pull up the house lights continuously with a fader in show mode without GO affecting it; a scene with an
s-curve fades in visibly softer.
Checked with domain tests (curves, mixing), `FaderApiTest` (DMX value at 50 %, export/import) and on the running test
server via the API.

## ✅ Iteration G – fixtures 💡
- **Multi-pixel fixtures:** `heads` block (or `head` per channel) in the YAML, attributes per head (`red@3`) with a
  fallback to the whole fixture, virtual dimmer per head, output in the `DmxRenderer`; programmer with head selection
  and head bar on the fixture card; compositions with "Each head separately" (Flyway `V17`). Example profile
  `generic/generic-led-bar-8-pixel.yml`. Note: the existing Eurolite LED BAR-6 QCL can only control its LEDs together
  according to its manual and therefore stays a fixture without heads
- **Import from the Open Fixture Library:** upload OFL JSON, Lumen writes a YAML profile into the library
  (`OflConverter`, `FixtureTypeYamlWriter`), fine channels → 16 bit, matrix → heads, simplifications as warnings. See
  [FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md#import-from-the-open-fixture-library)
- **Profile updates in the Mac app:** checksums of the installed version in `.bundled-profiles.properties`; unchanged
  profiles are replaced, edited ones stay (the new version next to them as `.shipped`), deleted ones do not come back

**DoD:** a chase runs across the 8 segments of a pixel bar; a moving head from the OFL can be patched without library
problems after the import; an app update brings corrected profiles without overwriting own changes.
Checked with tests (round trip OFL → YAML → library, renderer, chase across heads, profile updates) and on the running
test server (pixel bar on the DMX output, OFL import). Not yet tried with real pixel hardware and real OFL files of
big manufacturers.

## ✅ Iteration S – looks, compositions, scenes (three levels) 🧱
Concept and decisions: [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md). Replaces scenes with base +
composition copies and scenes on extra keys with three levels: **look** (fixed values) → **composition** (1–n steps
from looks with default and step times) → **scene** (set of shared compositions plus keys 1–8). Chase, rainbow & co.
become generators that create steps.

- ✅ S1 – domain & engine (looks, compositions, scenes, generators, playback and keys)
- ✅ S2 – database `V19` & conversion of existing projects and export files (version 1 → 2)
- ✅ S3 – REST & WebSocket
- ✅ S4 – UI (tab "Looks", compositions with generator, scenes with compositions and keys, show mode, cue list)
- ✅ S5 – docs updated

**DoD:** an existing project starts after the conversion and plays in show mode just as before; a new scene of three
shared compositions (static, color change from three looks, chase by generator) with a key is built and played in the
browser.
Checked: conversion of a real database (schema V12) and of the test database; in the browser (test server) built a
scene with a composition from the programmer and a generated rainbow, rainbow on key 1, in show mode GO and the key –
the DMX values show the rainbow over the scene. Not yet played with real hardware.

## ✅ Iteration CR – change requests after iteration S (2026-10-06) 🛠️
- **Parallel steps:** steps of a composition can start together ("Start": begin / with … / after …). They form a row
  with a shared fade/hold time and stand next to each other in the editor. Flyway `V20`
- **Faders play compositions**, "not in cue list" is dropped: existing faders play the first composition of their
  scene, scenes outside the cue list are removed (Flyway `V21`, likewise the conversion of old projects and the import
  of export files of versions 1 and 2). Export version 3
- **Programmer:** fixture list instead of tiles (long names readable), the control panel gets most of the room and
  arranges its sections in columns
- **Scenes:** hint on the way via compositions, "Save" at the bottom of the editor, list 30 % / editor 70 %
- **Show:** progress as a stack (before, now, next, the one after smaller and indented, "+ n more"), the eight key
  places are always reserved, the DMX tab only shows used channels of all universes, grouped by fixture
- **Party mode anew as a matrix** (replaces palettes, tap tempo and party buttons): fields with scene, composition or
  text, label, fill and border color, toggle/flash, groups (only one field on), the higher value wins, dividers between
  rows. Flyway `V22`, see [PARTY-MODE.md](PARTY-MODE.md)

**DoD:** `./build.sh` green. Checked in the browser (test server): composition with parallel steps, changing "Start",
scene editor, programmer list, setting up the party matrix and switching in party mode, show stack and DMX tab.

## ✅ Iteration L – light markers in the script 💡 (2026-10-06)
- **Light markers:** a line `//## Name (info text)##` in the scene text divides the scene into parts. In the scene
  editor every marker gets a section of its own "Compositions · 💡 Name" with the info text. Every part stands on its
  own: what should stay over the whole scene is chosen again per part, a part without a choice is dark. When the show
  switches over a marker in the script, the light of the part fades in with the scene's fade times; what is in both
  parts does not move (also when switching back)
- **Looks in scenes:** next to compositions, shared looks can be chosen directly (with level), at the start of the
  scene and in every part; they lie below the compositions. A look used like this cannot be deleted
- **Script in show mode:** ↑/↓ jump from speech to speech (the whole speech of a speaker is marked, stage directions
  in it count along); text without a speaker line by line, a marker always starts a new passage
- Flyway `V23` (`scene_composition.section`, table `scene_look`), export version 3 extended by `looks`/`sections` of
  the scenes, `PUT /api/projects/{id}/scenes/{sid}/sections`, text position with `section` (`PUT /api/playback/script`,
  WebSocket `playback.script`), status with `section`

**DoD:** checked in the browser (test server): scene with two markers, in show mode ↓ over marker 1 – the shared look
stays at 255, the new one fades in; ↑ back fades it out again.

## ⬜ Iteration M – MIDI controller 🎛️ (optional, possible at any time from iteration 7)
Concept: [MIDI.md](MIDI.md)
- Module `lumen-adapter-midi` with `javax.sound.midi`: device detection, hotplug, input events
- Controller profiles as YAML (`midi-controllers/`), a first profile for the existing controller
- Table `midi_mapping` per project (Flyway migration), actions for show and setup
- MIDI learn and MIDI monitor in the UI, soft takeover for faders
- LED feedback via `MidiFeedbackPort`

**DoD:** GO, back, grand master and blackout work via the controller and the button LEDs show the state. In setup
mode faders control group dimmers.

## 🟨 Iteration 10 – backlog (as needed)
- ✅ **Fade times per scene** (in, out, delay), see [PLAYBACK.md](PLAYBACK.md#fade-times-per-scene): the times apply
  to the GO into the scene, empty = project time. Flyway `V8`, travels with the project export. Scene editor with
  fields and a ⏱ badge in both scene lists; show mode shows "Fade in starts in …" during the delay. Checked with domain
  tests and in the browser (test server: a flash switches at once, the delay holds back the fade-in)
- ✅ **Rehearsal cross-fade** in setup mode: "⇢ Rehearse fade" in the scene editor plays the GO from the scene before
  (or from dark) into the chosen scene, as a stage preview (layer 75) with the same `Playback` engine as in show mode,
  i.e. with fade times, delay and compositions. `POST /api/projects/{id}/scenes/{sid}/rehearse-fade`, the status of
  the stage preview contains the running fade. Checked in the browser (test server)
- ✅ Along the way: robustness – on reload the playback reads the database outside the lock (the render loop does not
  wait), a scene deleted during the show stays until the next GO, after the end no newly created scene comes on stage.
  WebSocket watchdog (3.5 s without a message → reconnect), wake lock with NoSleep.js fallback for tablets over HTTP,
  `caffeinate` in `run.sh`, quieter test output
- ✅ **Shapes in the stage view:** spot, bar (LED bar) and strip (LED tape, e.g. DIY ESP), each with rotation and – for
  bar/strip – length. Bar and strip show every head of a multi-pixel fixture in its own color, "Auto" chooses by
  category and heads. Flyway `V18`, `PUT /api/projects/{id}/fixtures/{fid}/stage-shape`, travels with the project
  export. Checked in the browser (test server: 8-pixel bar, a bar rotated as a strip)
- External GO button: USB foot switch (as a keyboard), OSC (e.g. from QLab); MIDI see its own iteration
- sACN as a second `DmxOutputPort`
- Host names (mDNS, e.g. `artnet-dimmer.local`) as an Art-Net target – still open whether wanted
- Flash effect as a scene attribute (candle/fire flicker is now part of iteration K)
- PWA, Docker / Raspberry Pi as an alternative place to run
- ✅ Import of Open Fixture Library profiles (iteration G)
- ✅ **Printable cue list** for the stage management: "🖨 Cue list" in the scene list opens a print page
  (`#/print/cues`, light, A4, the header repeats on every page): number, scene, "GO on" (cue note), in/out/delay (own
  times in bold), auto-follow, running compositions, enabled extra keys, check boxes; below that the extra keys. PDF via
  "Save as PDF" in the print dialog instead of a PDF library in the server

## 🧪 Iteration 11+ – party mode 🪩 (step 1 replaced by the party matrix, see iteration CR)
Concept and decisions: [PARTY-MODE.md](PARTY-MODE.md). Step 1 is there and marked "experimental" in the UI, but is not
convincing yet. Put on hold (2026-10-05): before building on, the operation is to be rethought.
Roughly in steps:
1. ✅ **Tempo & palettes:** third operating mode "Party" (server state, programmer locked, PIN as in show mode, the
   theatre playback stands still and outputs nothing). Tap tempo (key `T`) with BPM and beat display, 10 fixed color
   palettes (RGB with white share, amber/UV/warm white on their own emitters, otherwise the nearest color wheel slot),
   intensity per group, **party buttons** (recorded from the programmer, toggle/flash, keys `1`–`9`) for gobo, prism &
   co. Party layer priority 60, Flyway `V9`, buttons travel with the project export. Position palettes postponed
   (they come with the movement figures). Checked in the browser (test server) and with an API test on the DMX output
2. **Effect engine:** uses the compositions from iteration K, adds BPM coupling (movement figures separately, see below)
3. **Executors:** freely assignable slots with fader, flash and toggle (a further development of the extra keys and
   chasers from iteration K)
4. **Live tools:** flash all, strobe all, fog, speed master, save the look as a scene
5. **Extras:** beat detection via microphone, MIDI mappings for executors (requires the MIDI iteration)

**DoD (steps 1–3):** at a party, colors can be changed on the beat via tablet, and effects can be dosed via executor
faders.

## ✅ Iteration 12 – moves: figures for pan/tilt fixtures 🌀
Concept decided on 2026-10-08: [MOVEMENT.md](MOVEMENT.md). Coordinates in DMX values, absolute, offset in steps.
Position only via moves (also standing heads: a figure with one point); looks and presets without pan/tilt.
1. Figures: drawn point sequences without fixtures, figure editor with templates
2. Moves: figure + fixtures + size, shift, rotation, duration, loop/once/ping-pong, offset, mirror; preview
3. Use in scenes, light markers, party and keys; "Record position" in the programmer; project files version 4
4. Move in dark per scene: dim out, move in the dark, dim in
5. Pan/tilt limits per fixture in the patch, applying to playback (not to the programmer)

Implemented on 2026-10-08 (all five steps): new setup tab **Moves** with figure and move editor (preview in the
browser, "Show on stage"), moves in scenes, light markers, keys and party, "Record position", move in dark, limits in
the patch with a frame in the XY pad. Details and deviations in [MOVEMENT.md](MOVEMENT.md#implementation-2026-10-08).

**DoD:** a moving head moves an eight in a scene, the next scene only changes the color and the head stays where it
is; with "Move in dark" it travels invisibly to its new position. Checked with domain, repository and API tests and via
REST on the running server (Art-Net off); the UI is checked with svelte-check and Vitest, in the browser only looked at.

## ✅ Iteration Q – quick setup, party gradients, pages & pads ⚡ (2026-10-12)
- **Quick setup** in the patch (optional, while adding fixtures or for patched ones): intensity looks 0/50/100 %,
  color looks red/orange/green/blue/violet (mixed, CMY or the closest color wheel slot) with a color palette of
  presets they refer to (other wheel slots as further presets), moves line/circle/eight for pan/tilt fixtures, each
  look with a composition, plus a party row of buttons and a row of gradients – one button for all the chosen
  fixtures
- **Several fixtures selected in the patch**: shift-click in the channel grid or checkboxes in a new list view (shift
  for a range); for one fixture type the side panel offers quick setup for all of them and "Locate all"
- **Party fields over several columns** and **gradient fields** (intensity or color) to pick by touch; dragging goes
  over the WebSocket (`party.slide`). Flyway `V28`, project export with `span`/`fader`
- **Party pages** (tabs, up to 12; groups reach over all of them), fields **several rows high**, **pan/tilt pads**
  (quick setup adds one for moving heads) and party mode **filling the window** without scrolling. Flyway `V29`,
  project file version 5
- Details: [PARTY-MODE.md](PARTY-MODE.md#quick-setup-2026-10-12)

**DoD:** four PARs patched with quick setup give eight looks and two party rows; a gradient sets the intensity or the
color of all four; two movers get a pad that points both. Checked with domain, application, repository (incl. the
migration of an existing matrix) and API tests (DMX output, export/import) and via REST on the running server (Art-Net
off); the UI with svelte-check, Vitest and in Chrome (patching with quick setup, the dialog for patched fixtures,
selecting several fixtures in the grid and the list and setting them up together, the matrix editor with pages and a
pad, touching and dragging gradients and the pad in party mode, checked on the DMX output, no scrolling at 1920 × 994).

---

## Way of working
- One branch per iteration, small commits, domain logic test-driven.
- After every iteration: update the status here, note what was learned.

## Decisions (2026-10-02)
| Topic | Decision |
|---|---|
| Stay | until GO, optional **auto-follow** per scene (iteration 6/7) |
| Place to run | **laptop at the control desk**: server, browser and MIDI controller on one device; Pi/Docker only backlog |
| Java | **Java 26** (already installed). Support ends March 2027, plan an upgrade to the next release afterwards |
| Frontend | **Svelte 5** + TypeScript + Vite |
| Access | **PIN for setup mode**, show mode without login; the server listens only on `localhost` by default |
| Name | **Lumen**, base package `de.vinzens.lumen` |

## Open questions
- Further fixtures besides the own ESP8266 dimmer (profile `fixture-types/diy/`)? Until then generic example profiles
  (RGBW PAR, dimmer, moving head).
- Which MIDI controller? Until then generic MIDI.
