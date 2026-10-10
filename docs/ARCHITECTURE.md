# Architecture

## Overview: hexagonal (ports & adapters)

```
                    ┌────────────────────────────────────────────────┐
  Driving adapters  │                  Application                   │  Driven adapters
  (inbound)         │                                                │  (outbound)
                    │   Inbound ports           Outbound ports       │
 ┌──────────────┐   │  ┌────────────────┐     ┌───────────────────┐  │   ┌──────────────────┐
 │ REST API     │──▶│  │ ProjectUseCase │     │ DmxOutputPort     │──│──▶│ Art-Net UDP      │
 └──────────────┘   │  │ PatchUseCase   │     └───────────────────┘  │   └──────────────────┘
 ┌──────────────┐   │  │ Programmer-    │     ┌───────────────────┐  │   ┌──────────────────┐
 │ WebSocket    │──▶│  │  UseCase       │     │ FixtureLibrary    │──│──▶│ YAML folder      │
 └──────────────┘   │  │ SceneUseCase   │     └───────────────────┘  │   └──────────────────┘
 ┌──────────────┐   │  │ PlaybackUseCase│     ┌───────────────────┐  │   ┌──────────────────┐
 │ MIDI (USB)   │──▶│  │ MidiInput-     │     │ MidiFeedbackPort  │──│──▶│ MIDI LEDs        │
 └──────────────┘   │  │  UseCase       │     └───────────────────┘  │   └──────────────────┘
                    │  │                │     ┌───────────────────┐  │   ┌──────────────────┐
                    │  └───────┬────────┘     │ ProjectRepository │──│──▶│ SQLite           │
                    │          ▼              │ SceneRepository   │  │   │ (Flyway, JDBC)   │
                    │    ┌──────────┐         └───────────────────┘  │   └──────────────────┘
                    │    │  Domain  │         ┌───────────────────┐  │   ┌──────────────────┐
                    │    └──────────┘         │ StateNotifier     │──│──▶│ WebSocket push   │
                    │                         └───────────────────┘  │   └──────────────────┘
                    └────────────────────────────────────────────────┘
```

**Rule:** dependencies always point inwards. The domain knows neither Spring nor YAML, SQL or UDP.
**ArchUnit** tests guard this.

## Maven modules

```
lumen/
├── pom.xml                       parent, versions, Java 26
├── lumen-domain/                 plain Java, no dependencies
├── lumen-application/            use cases + port interfaces, depends on domain only
├── lumen-adapter-artnet/         DmxOutputPort → Art-Net UDP
├── lumen-adapter-library-yaml/   FixtureLibrary → YAML folder
├── lumen-adapter-sqlite/         ProjectRepository, SceneRepository → SQLite (Flyway + JdbcClient)
├── lumen-adapter-web/            REST + WebSocket controllers, DTOs
├── lumen-adapter-midi/           (optional, later) USB MIDI controller via javax.sound.midi, see MIDI.md
├── lumen-frontend/               Svelte/Vite, the build ends up as static resources in the JAR
└── lumen-app/                    Spring Boot main, wiring, application.yml
```

Adapter and frontend modules are only created in the iteration that needs them.

Only `lumen-app` and the adapters know Spring. The use cases are wired as beans in `lumen-app`, so that
`lumen-application` stays free of frameworks.

## Data sources

| Source | Content | Docs |
|---|---|---|
| YAML folder `fixture-types/` | Fixture library (what a fixture can do) | [FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md) |
| SQLite `data/lumen.db` | Projects: universes, patch, groups, scenes, fade times | [PROJECT-DATA.md](PROJECT-DATA.md) |
| `application.yml` | Technical settings: paths, port, Art-Net interface, render rate | – |

## Domain model

```
── Library ──────────────────────────────────────────────────────────────
FixtureType            fixture model (from YAML)
 └─ modes: List<Mode>
      └─ channels: List<ChannelDefinition>  (function, resolution 8/16, default, ranges, fadeBehavior)

── Project ──────────────────────────────────────────────────────────────
Project                name, fadeIn, fadeOut (Duration), universes, fixtures, groups, scenes
Universe               number, Art-Net port address (net/subnet/universe), target (IP | broadcast)
PatchedFixture         name, FixtureType + mode, DmxAddress(universe, 1..512), position(x, y)
Group                  set of PatchedFixtures
Scene                  number ("2.5"), name, cueNote, compositions, keys (see LOOKS-COMPOSITIONS-SCENES.md)
AttributeValues        function → normalized value (0..1)

── Runtime ──────────────────────────────────────────────────────────────
OutputLayer            interface: delivers attribute values + priority on every tick
LayerMerger            combines all layers (HTP/LTP)
Programmer             live values in setup mode (an OutputLayer)
Playback               scene list, current/next scene, running cross-fade
Crossfade              snapshot (old) + target scene (new), start time, pause state
OutputState            computed attribute values of all fixtures
DmxFrame               512 bytes per universe
```

Important domain logic (all unit-testable without Spring, time via an injected `Clock`):
- **Patch validation:** overlaps, channel > 512, missing type/mode
- **Mixing on a scene change:** intensity directly from old to new (upwards with the fade-in, downwards with the
  fade-out), LTP for all other attributes, see [PLAYBACK.md](PLAYBACK.md)
- **Mapping OutputState → DmxFrame:** functions onto channels/offsets, 16 bit, virtual dimmer, `invert`
- **Grand master & blackout** as the last stage before output

## DMX engine

- **Render loop** (`ScheduledExecutorService`, ~40 Hz, configurable; Art-Net max. ~44 Hz).
- On every tick: all active **output layers** (`OutputLayer`) deliver attribute values → **layer merger**
  (intensity HTP, the rest LTP/priority) → `OutputState` → master/blackout → `DmxFrame`s → `DmxOutputPort`.
- Layers in theatre mode: `Programmer` and `Playback` (incl. fade progress). The [party mode](PARTY-MODE.md) adds
  further layers without rebuilding the engine.
- All universes of the active project are sent continuously (many nodes expect regular packets).
- Thread safety: immutable state, swapped via `AtomicReference`. Commands (GO, value changes) reach the loop through
  a queue.
- **Stability comes first:** an exception in a tick must not end the loop (log it, keep sending the last valid frame).

## Art-Net adapter

- UDP port **6454**, packet **ArtDmx** (OpCode `0x5000`, protocol version 14), 512 bytes of data.
- 15-bit port address: `Net (7) | SubNet (4) | Universe (4)`.
- Target per universe:
  - **Auto** (default): unicast to all nodes that report this universe in their ArtPollReply, plus optional manual
    IPs (for nodes without ArtPoll support)
  - **Fixed IPs**: unicast to the given addresses only
  - **Broadcast**: to everyone – every device gets every universe; unreliable over Wi-Fi
- Sequence number per universe (1–255).
- **Discovery:** ArtPoll every 3 s to the broadcast address; nodes answer with an ArtPollReply to port **6454** of the
  controller. Lumen listens there (with `SO_REUSEADDR`/`SO_REUSEPORT`, so that e.g. QLC+ can run alongside). A node
  counts as offline 10 s after its last reply and is forgotten after 5 min. If the port cannot be opened, discovery
  stays empty and everything else keeps running.
- Packets are sent from a random local port; target port, bind address and broadcast address are set in
  `application.yml` under `lumen.artnet.*`. In tests the output is switched off (`lumen.artnet.enabled: false`).

### Testing without hardware

- **Look at DMX values:** `GET /api/output/universes/1/dmx` returns the 512 channel values of the active project.
- **Capture Art-Net:** Wireshark with the filter `artnet` (port 6454) shows the ArtDmx packets including channel values.
- **Art-Net monitor:** QLC+ (Input/Output → Art-Net as input, then "DMX Monitor"). If QLC+ runs on the same computer,
  set the universe in Lumen to unicast `127.0.0.1`.
- **Quick test with curl** (requires an active project with a patched fixture):
  ```bash
  curl -X PATCH -H 'Content-Type: application/json' \
       -d '{"dimmer": 1, "red": 1}' http://127.0.0.1:8080/api/programmer/fixtures/1
  ```

## Web API

**Setup**

| Endpoint | Purpose |
|---|---|
| `GET /api/library/fixture-types`, `GET /api/library/issues` | Fixture library |
| `CRUD /api/projects`, `GET /api/projects/active`, `POST /api/projects/{id}/activate` | Manage projects, incl. global fade times (`fadeInMs`, `fadeOutMs`) and fade curve (`fadeCurve`: `linear`, `s-curve`, `square`) |
| `GET /api/projects/{id}/export`, `POST /api/projects/import` | Project as a JSON file (format see [PROJECT-DATA.md](PROJECT-DATA.md)) / import as a new project |
| `GET /api/backups` · `POST /api/backups` | List DB backups (incl. folder) · create a backup now |
| `GET /api/access` · `POST /api/access/unlock` `{pin}` · `/lock` | PIN status of this browser, unlock (403 wrong PIN, 429 after 5 failed attempts), lock |
| `PUT /api/access/pin` `{pin\|null}` · `PUT /api/access/network` `{enabled}` | Set/change/remove the PIN · network access (takes effect after a restart) |
| `CRUD /api/projects/{id}/universes` | Universes & Art-Net targets: `mode` (`auto`/`unicast`/`broadcast`) + `ipAddresses` |
| `PUT /api/projects/{id}/fixtures/positions` `{positions: {fixtureId: {x, y} \| null}}` | Stage positions (0–1) of several fixtures at once, `null` = not on stage |
| `GET/PUT/DELETE /api/projects/{id}/stage/plan` | Floor plan image (PUT with `Content-Type: image/png\|jpeg\|webp` as body) |
| `CRUD /api/projects/{id}/looks` · `POST …/looks/{lid}/record` · `/load` · `/duplicate` · `GET …/revisions` · `POST …/revisions/{rid}/restore` | Looks: record from the programmer, load into the programmer, history and undo, see [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md) |
| `CRUD /api/projects/{id}/compositions` · `POST …/compositions/{cid}/duplicate` · `/record-step` · `/generate` | Compositions made of look steps, a step from the programmer, generate steps with a generator |
| `PUT /api/projects/{id}/scenes/{sid}/compositions` · `/sections` · `/keys` | Compositions and looks of a scene, light of the parts after light markers, keys 1–8 (each the whole list) |
| `POST /api/projects/{id}/compositions/{cid}/stage-preview` · `POST …/scenes/{sid}/stage-preview` · `GET/DELETE /api/stage-preview` | Show a composition or a whole scene on stage to try it out (setup mode only) |
| `POST /api/library/reload` | Read the fixture library again (otherwise happens automatically after file changes) |
| `POST /api/library/import/ofl?manufacturer=…&replace=false` (body: OFL JSON) | Take a fixture from the Open Fixture Library into the library as a YAML profile, response with `warnings` (409 if the ID already exists) |
| `GET /api/artnet/nodes` | Nodes found via ArtPoll (IP, name, reported universes, online) |
| `CRUD /api/projects/{id}/fixtures`, `GET /api/projects/{id}/patch/issues` | Patch |
| `CRUD /api/projects/{id}/groups` | Groups |
| `CRUD /api/projects/{id}/scenes` | Scene list, sorted by number; `POST` with `"record": true` takes the programmer, without `number` the next whole number |
| `POST /api/projects/{id}/scenes/{sceneId}/record` · `/load` | Overwrite the scene with the programmer (no body) or take over parts (`{merge: true, fixtureIds, groups}`) · load the scene into the programmer |
| `POST …/scenes/{sceneId}/duplicate` `{number}` | Copy a scene with its times, text, compositions and keys |
| `CRUD /api/projects/{id}/figures` · `POST …/figures/{fid}/duplicate` | Figures for moves: points in DMX values (`x` = pan, `y` = tilt), `closed`; delete only without moves (409), see [MOVEMENT.md](MOVEMENT.md) |
| `CRUD /api/projects/{id}/moves` · `POST …/moves/{mid}/duplicate` · `POST …/moves/{mid}/stage-preview` | Moves: figure + fixtures (each with its own shift, mirror, reverse), size in %, shift in DMX values, `rotation`, `durationMs`, `repeat`, `stepOffset`, `onceStart`; delete only when nothing plays it (409) |
| `POST /api/projects/{id}/moves/record-position` `{moveId\|null, name, fixtureIds}` | "Record position": programmer positions as a move with one point (centre + offset per fixture) |
| `GET /api/projects/{id}/presets` · `POST` `{name, group, fixtureIds}` · `PUT /{pid}` `{name}` · `DELETE /{pid}` | Record presets (color, beam) from the programmer, rename, delete |
| `POST …/presets/{pid}/record` · `/apply` `{fixtureIds}` | Update a preset from the programmer (all looks using it follow) · apply it in the programmer |
| `GET /api/projects/{id}/faders` · `PUT /{slot}` `{name, compositionId}` · `DELETE /{slot}` | Assign a composition to fader 1–8 (e.g. house lights) |

Project endpoints always name the project ID explicitly in the path (no hidden "current project" state).
`CRUD` = `GET` (list), `POST` (create), `PUT /{id}` (replace), `DELETE /{id}`.
Errors come as Problem Details (RFC 9457): 400 with an `errors` list, 403 with `"locked": true` (PIN required), 404, 409.

**Programmer**

| Endpoint | Purpose |
|---|---|
| `GET /api/programmer` · `PATCH /api/programmer/fixtures/{id}` · `DELETE /api/programmer/fixtures/{id}` · `DELETE /api/programmer` | Live values (active project) |
| `GET /api/programmer/presets` | which presets the programmer values come from (`{fixtureId: {color: presetId}}`) |
| `PATCH /api/programmer/fixtures` `{fixtureIds, values}` · `POST /api/programmer/clear` `{fixtureIds}` | Set/clear a selection; each fixture only gets its own attributes |
| `PATCH /api/programmer/groups/{id}` | Live values for all members of a group |

**Show**

| Endpoint | Purpose |
|---|---|
| `GET /api/playback` · `POST /api/playback/go` · `/back` · `/pause` · `/finish` · `/stop` | Control the show, status |
| `POST /api/playback/keys/{slot}/press` · `/release` | Press / release key 1–8 (toggle or flash) |
| `PUT /api/playback/faders/{slot}` `{level}` | Set a fader (e.g. house lights) to 0–1, also in setup mode, without PIN |
| `PUT /api/playback/script` `{sceneId, line, section}` | Position in the script of the scene on stage; all clients follow, without PIN |
| `POST /api/playback/resume` · `DELETE /api/playback/resume` | After a restart, continue at `resumeSceneId` · discard the offer |
| `PUT /api/playback/next` `{ sceneId }` | Pick the next scene (jump) |
| `GET/PUT /api/mode` `{ mode: setup\|show\|party }` | Operating mode; `show` and `party` clear and lock the programmer, back to `setup` only when unlocked |
| `GET/PUT /api/output/master` `{level}` · `PUT /api/output/blackout` `{active}` | Grand master / blackout |
| `GET /api/output/universes/{n}/dmx` | Raw DMX (monitor), `n` = universe number in the active project |
| `GET /api/output/health` | Render loop and Art-Net of the last second: `level` (ok/warning/error), `problems`, rates |

**Attribute values** are normalized (0.0–1.0) and addressed by attribute ID, e.g.
`{"dimmer": 1, "red": 0.5, "generic:Program speed": 0.2}`. Generic channels are called `generic:<name>`. Heads of
multi-pixel fixtures get `@<head>` appended (`red@3`); the attribute without a head applies to all heads without a
value of their own.

**WebSocket `/ws`:** the server collects changes and sends them at most every 33 ms (JSON):

| Direction | Message |
|---|---|
| Server → client | `{"type":"programmer","values":{…},"presets":{…}}` (all values and their presets), `{"type":"master","level":…,"blackout":…}`, `{"type":"playback","status":{…}}`, `{"type":"mode","mode":…}`, `{"type":"health","health":{…}}` (every second), `{"type":"patch"}` / `{"type":"scenes"}` / `{"type":"nodes"}` / `{"type":"projects"}` / `{"type":"access"}` / `{"type":"library"}` / `{"type":"presets"}` / `{"type":"faders"}` (client reloads via REST); after subscribing `{"type":"output","values":{…}}` (values as sent, before the master) and `{"type":"dmx","universe":n,"values":[…512]}`, `{"type":"error","message":…}` |
| Client → server | `{"type":"programmer.set","fixtureIds":[…],"values":{…}}`, `{"type":"master.set","level":…}`, `{"type":"blackout.set","active":…}`, `{"type":"playback.go"}` (likewise `.back`, `.pause`, `.finish`), `{"type":"playback.script","sceneId":…,"line":…,"section":…}`, `{"type":"keys.press","slot":1}` (likewise `keys.release`), `{"type":"fader.set","slot":1,"level":0.5}`, `{"type":"subscribe","topics":["output","dmx:1","preview:7"]}` (replaces the subscription; live output at most every 100 ms and only on change; `preview:<id>` delivers `{"type":"preview","compositionId":…,"values":{…}}` for the editor) |

On connecting, a client immediately gets the programmer, master, playback status, mode and health. With a PIN set,
`programmer.set` needs an unlocked browser; after unlocking, the client reconnects. The client interpolates the fade
progress itself (the status contains the elapsed time when it was sent). Internally the application layer publishes
changes through `StateEvents`; the web adapter subscribes to them – so the dependencies keep pointing inwards.

## Operational safety (it's theatre!)

- Output runs independently of the browser: if the tab closes or the Wi-Fi drops, the light stays as it is.
- The current scene is persisted (`playback_state`), as is the operating mode (`app_setting`). After a restart Lumen
  starts in the same mode, the light stays dark and show mode offers to continue at the saved scene. Saving happens on
  a thread of its own; the render loop never waits for the DB.
- Automatic DB backup (`VACUUM INTO`, consistent while running) at start, on a project change and when show mode
  starts, into `data/backups/`; the newest 30 are kept (`lumen.backup.*`). To restore: quit Lumen and copy the file
  over `data/lumen.db`.
- **Watchdog:** the render loop catches every failure in a tick (including `Error`). If a tick hangs for more than
  2 s, the watchdog replaces the render thread. Render rate and Art-Net packets/s are measured every second and sent
  to all clients; the header shows the state (e.g. "Art-Net 40/s", a warning on send errors or without receivers).
- **Soak test:** `ShowSoakTest` plays a 3 h show (40 Hz, auto-follow, GO, pause, master, blackout) on a simulated
  clock in a few seconds and checks that the heap does not grow.

## Operation & access

- The target platform is a **laptop at the control desk**: server, browser and MIDI controller run on the same
  machine, the Art-Net node is connected by network cable.
- By default the HTTP server only listens on **`127.0.0.1`**. Access from tablets/phones on the network is enabled
  under Settings (takes effect after a restart, then it listens on `0.0.0.0`) or permanently with
  `lumen.network-access=true`.
- **Setup PIN** (optional): reading and operating the show (playback, master, blackout, switching to show mode) always
  work without a PIN. All other writing endpoints and `programmer.set` via WebSocket need an HTTP session that was
  unlocked with the PIN. Starting show mode locks all browsers again, so the way back to setup asks for the PIN. The
  PIN (4–12 digits) is stored as a salted PBKDF2 hash in `app_setting`; after 5 wrong entries, unlocking is blocked
  for 30 s.
- Implemented without Spring Security: a `HandlerInterceptor` in the web adapter checks the session, the logic
  (`AccessService`) lives in the application layer. A lock "epoch" counts up when all browsers are locked; a session
  is unlocked as long as its epoch is current.
