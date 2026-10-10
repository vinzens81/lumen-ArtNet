# Moves – figures for pan/tilt fixtures

> **Status:** implemented on 2026-10-08 (stages 1–5), see [Implementation](#implementation-2026-10-08). Decided on
> 2026-10-08.

## What it is about

Until now moving heads and wash lights only had **fixed positions** (XY pad in the programmer, stored in looks).
Moves bring **movement**: a head follows a drawn figure, endlessly or once as a "journey" from A to B. It can move
alone or together with other heads that join in with an offset.

There is a clear separation: **looks say what the light looks like** (brightness, color, gobo, zoom, …), **moves say
where it points** – even if a head just stands still. In playback, pan and tilt come exclusively from moves.

The model is the figure editor with movement scene in DMXControl 2: figures are drawn without fixtures; assigning them
to fixtures with size and placement comes afterwards.

## Decisions (2026-10-08)

| Topic | Decision |
|---|---|
| Coordinates | **DMX values**, not degrees: X = pan, Y = tilt, each 0–255 (internally finer, 16 bit) |
| Placement | **Absolute**: the figure lies where it is drawn and shifted. A look below does not move it |
| Offset between fixtures | **In steps**: fixture 1 joins at point 1, fixture 2 at point 1 + offset, … |
| Place in the model | Figure and move are objects of their own like looks; a move plays in scenes, light markers, party and keys |
| Once with offset | **both selectable**: one after another ("snake") or at the same time from offset points |
| Position only via moves | **Strict**: looks, compositions and presets no longer contain pan/tilt. No conversion of existing data is needed, there are no movements yet |
| Fixed position | a move whose figure has only **one point** |
| Placement per fixture | every fixture of a move also has an **own shift**, so that heads hanging in different places point at the same spot |
| Limits per fixture | apply to playback, **not to the programmer** |
| Moving in the dark | settable **per scene**: dim out, move, dim in, instead of travelling across the stage lit |
| Rotation | in the DMX grid; a rotated circle becomes an ellipse on stage if pan covers more degrees than tilt |

## Terms

```
Figure  "Eight"        just the shape: points in order, open or closed – without fixtures
  └─ Move "Eight above the stage"  figure + fixtures + size, shift, rotation, tempo, repeat, offset
       └─ scene 4 / light marker / party field / key   plays the move
```

- A **figure** belongs to the project and can be used by several moves. If it is changed, all moves change with it
  (as with presets).
- A **move** is what you pick in a scene, like a look or a composition.
- So a simple spot on the singer consists of two parts: look "White 80 %" + move "Singer centre". In return, every
  color can be combined with every position, and a position is maintained in one place.

## Stage 1 – figure

| Field | Meaning |
|---|---|
| Name | e.g. "Eight", "Path stage left → centre" |
| Points | ordered list of (X, Y) in the range 0–255, at least 1 – **one point = fixed position** |
| Closed | yes: after the last point it goes back to the first (circles, eights); no: open path (journeys) |

The points are connected **with straight lines**. Smooth curves come later (see [Later](#later)).

### Figure editor

```
┌ Figure: [ Eight          ▾] [+ New] [Duplicate] [Delete] ───────────────────────────────┐
│ Template: [Circle ▾] Points: [8] [Apply]        ☑ Closed   ☑ Show numbers   [Clear]     │
│  255 ┼───────────────────────────────────────────────┐                                  │
│      │            ②────────③                         │   Points                         │
│      │          ╱            ╲                       │   1  X  64   Y 128   ✕            │
│ Tilt │       ①                 ④                     │   2  X  96   Y 192   ✕            │
│      │        ┆                 │                    │   3  …                           │
│      │        ⑥────────────────⑤   (┆ = closing line) │                                  │
│    0 ┼──────────────── Pan ─────────────────────────┘ 255                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Clicking** into the area appends a point. Consecutive points are connected; the line from the last to the first
  point (with "Closed") is dashed. Arrows on the lines show the direction.
- **Dragging** moves a point, **clicking a line** inserts a point there, **Alt+click** or ✕ in the list deletes it.
  The list on the right allows exact values and reordering.
- **Numbers** at the points show the order. They matter for the offset of the fixtures.
- **Templates** with a selectable number of points: circle, eight, horizontal line, vertical line, triangle,
  rectangle, random. "Apply" replaces the points; afterwards everything can be changed by hand.
- Grid with guides at 64/128/192; snap to whole values, with Shift to the 16 grid.

## Stage 2 – move

| Field | Meaning | Default |
|---|---|---|
| Name | e.g. "Eight above the stage" | |
| Figure | choice from the library or "New figure…" (opens the figure editor) | |
| Fixtures | ordered list of fixtures with pan **and** tilt; the order defines the offset | |
| Size X / Y | scaling of the figure around its centre, separately for pan and tilt, in % | 100 % |
| Shift X / Y | shift of the whole figure in DMX values (−255 … +255) | 0 |
| Rotation | rotation around the centre of the figure in degrees (view in the DMX grid) | 0° |
| Duration | time for one round of the figure in seconds (hidden for one point, like repeat and offset) | 8 s |
| Repeat | **loop** (endless), **once** (once, stays at the end), **ping-pong** (there and back) | loop |
| Offset | steps by which every further fixture joins later (0 = all in sync) | 0 |
| Start for once | **one after another** (all make the same journey, fixture *i* starts later) or **together** (all start at once, fixture *i* from point `1 + i × offset`) | one after another |

Per fixture, as an exception in the fixture list:

| Field | Meaning |
|---|---|
| Shift X / Y | own correction in DMX values, in addition to the shift for all |
| Mirror X / Y | pan or tilt mirrored around the centre of the figure, e.g. for heads left and right moving symmetrically |
| Reverse | follows the figure in the opposite direction |

Size, shift and rotation apply to **all** fixtures of the move (step 4 of the idea). The **shift per fixture**
compensates for the heads hanging in different places: without it, four heads at the same DMX point point at four
different spots on stage, and each draws the eight somewhere else. In the editor it is adjusted per fixture with a
small XY pad while the head shows the figure on stage.

```
Move "Singer centre"   figure: 1 point (128, 100)
  MH 1  shift X −18  Y +6
  MH 2  shift X  −5  Y +4
  MH 3  shift X  +7  Y +4
```

### Move editor

```
┌ Move: [ Eight above the stage ▾] [+ New move] ────────────────────────────────────────────┐
│ Figure [ Eight ▾] [Edit figure]         Fixtures: [MH 1] [MH 2] [MH 3] [MH 4]  [+ Add]     │
│ ┌──────────────────────────────┐   Size    X [ 80 %]  Y [ 50 %]                            │
│ │   ·  · (figure grey)         │   Shift   X [ +20 ]  Y [ −10 ]                            │
│ │  ●1 ───── ●2   (figure after │   Rotate  [ 0° ]                                          │
│ │   ╲        ╲   size/shift)   │   Time    [ 8.0 s ] per round   Repeat (Loop|Once|Ping-pong)│
│ │    ●3 ──── ●4  ● = fixture,  │   Offset  [ 2 ] steps per fixture                         │
│ │                animated)     │   ─────────────────────────────────────────                │
│ └──────────────────────────────┘   MH 1  ☐ Mirror X ☐ Mirror Y ☐ Reverse   starts at ①     │
│ [▶ Preview] [Show on stage]        MH 2  ☑ Mirror X ☐ Mirror Y ☐ Reverse   starts at ③     │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

- The area shows the base figure faintly and the figure after size, shift and rotation strongly. A named dot per
  fixture moves along it (preview in the browser, without output).
- **Size and shift** can also be set directly in the area: dragging the figure shifts it, the corner handles change
  the size.
- "Show on stage" plays the move on stage as with compositions (moves real heads), with the known hint in the header
  and Stop.
- Fixtures without pan/tilt are not offered.
- The column "starts at" shows at which point every fixture joins.

## Stage 3 – use

- **Scenes and light markers:** in every part of the scene there is a choice of **moves** next to looks and
  compositions. As with compositions, a move that is in both scenes or parts **keeps running without interruption**.
- **Party:** a field of the matrix can switch a move on and off (toggle or flash).
- **Keys** of a scene can play a move.
- **Programmer:** pan/tilt is still set with the XY pad; the programmer overrides everything as before, including a
  running move. When saving, it separates:
  - **Record look** saves everything except pan/tilt.
  - **Record position** creates a move with one point (or updates it): the point is the centre of the positions of
    the selected fixtures, every fixture gets its deviation from it as its own shift.
  - **Record look + position** does both in one step and can put both into the scene right away.
- **Without a move** a head keeps its last position (existing rule: what the new scene does not set stays). Changing
  only the color therefore does not send the heads back to the centre.

## How it is computed

Everything as a pure function of time, like the other compositions. With `t` = time since the start × tempo factor:

```
1. Path:      figure → polyline (closed: back to the first point); length L, point k at distance s_k
2. Progress of a fixture i:
     Loop:       p = frac( t / duration + s_(i × offset mod n) / L )
     Ping-pong:  like loop, but every second round backwards
     Once:       like loop without frac, stop at 1; offset depending on "start" (see below)
     Reverse:    p → 1 − p
3. Point on the path at distance p × L          → (x, y), even speed along the path
4. Mirror:      x → 2·cx − x  or  y → 2·cy − y        (c = centre of the figure, centre of the bounding box)
5. Placement:   (x, y) = c + shift + shift_fixture + rotation( size ⊙ ((x, y) − c) )
6. Clamp:       to 0–255 and to the limits of the fixture (see below)
7. Output:      pan = x / 255, tilt = y / 255  (normalized, 16 bit for fixtures with a fine channel)
```

- **Even speed:** time is distributed by the length of the segments, not equally per point. Otherwise the head would
  race over long distances and crawl over short ones.
- **Offset in steps:** with loop and ping-pong, fixture *i* starts at point `1 + i × offset` (cyclic). With **once**
  (journey), all follow the same path from the beginning, fixture *i* starts `i × offset` steps later, i.e. by the
  time the path needs up to point `1 + i × offset`. Until then it stands on the start point. That gives the "snake"
  that makes the same journey one after another. With start **together**, all start at once, fixture *i* from point
  `1 + i × offset`, and each stops at the end of the path; for open figures the path is shorter for later fixtures,
  for closed figures each makes exactly one round up to its start point.
- **Mixing:** moves only set pan and tilt, looks and compositions everything else. If two moves with the same fixture
  are in a scene, the later one in the list wins (LTP). A move has no level.
- **Transitions:** at GO a head travels with the fade-in from its position to the path and then follows it. If it was
  dark at GO, it jumps there at once (existing rule for continuous attributes). If the same move keeps running in the
  new scene, there is no transition.
- **Fine channels:** movements stutter visibly with 8 bit. The renderer already outputs pan/tilt with 16 bit if the
  profile has a fine channel.

## Moving in the dark (per scene)

If a head travels lit from one position to the next at GO, the audience sees the light spot wander across the stage.
Often the head should instead **dim out, move in the dark and dim in again at the new place** ("move in black" on
lighting desks).

The scene editor has these fields with the fade times:

| Field | Meaning | Default |
|---|---|---|
| **Move in dark** | on/off for the GO into this scene | off |
| **Dim out** | time to dim out the heads that move | 0.5 s (project) |
| **Travel** | time the heads get to move in the dark; Lumen does not know the actual motor speed | 1.5 s (project) |

Sequence for a head that is lit at GO and **points somewhere else** in the new scene (more than 2 DMX values
difference in pan or tilt):

```
GO
│ Dim out 0.5 s │ Travel 1.5 s           │ fade-in of the scene 3 s
│█▇▅▃▁          │                        │▁▃▅▇█
│ old position  │→ jumps to the new position, color/gobo jump along
```

- Pan/tilt **jump** to the target at the start of "Travel". The head then moves as fast as it can, i.e. in the dark.
  Color, gobo and zoom jump at the same time, so their change also stays invisible.
- Afterwards the head dims up to its new brightness with the **fade-in of the scene**.
- A running move (figure) only starts its time when the head dims in, so that it is not already under way.
- **If the head was dark at GO**, "Dim out" is skipped. It jumps to the new position at once and waits out "Travel"
  before dimming in. So it does not appear before it has arrived.
- **Heads that stay in the same place**, and all fixtures without pan/tilt, cross-fade normally with the times of the
  scene, without a dark phase.
- If **the same move** keeps running in the new scene, that is not a change of position: there is no dark phase.
- The **delay** of the scene applies as before: the heads dim in at the earliest after the delay, i.e. after
  `max(delay, dim out + travel)`.
- The stay time (auto-follow) only starts when all heads have dimmed in. The progress display in the show includes the
  dark phase.
- **Light markers** of a scene with "Move in dark" behave the same (with dim out and travel, without delay, as usual
  for light markers).
- Party and keys: without a dark phase for now (see [Later](#later)).

## Limits per fixture

In the **patch**, every fixture with pan/tilt optionally gets an allowed range: pan min/max and tilt min/max in DMX
values. The limits apply to **playback**, i.e. to all moves in scenes, party and keys. So no head shines into the
audience or the fly loft, no matter which scene the value comes from.

The **programmer** is exempt: when setting up, you should be able to reach every point. The XY pad shows the allowed
range as a frame. If a position outside it is saved as a move, it is only limited in playback.

## What changes in existing parts

| Area | Before | With moves |
|---|---|---|
| Looks | can contain pan/tilt | no pan/tilt any more; the look editor does not show the group "Position", the API rejects it |
| Compositions | steps are looks | therefore also without pan/tilt; generators do not offer pan/tilt |
| Presets | color, position, beam | only color and beam; a saved position is a move with one point |
| Programmer | "Record" saves everything into a look | Record look / Record position / Record look + position (see above) |
| Party | fields switch scenes and compositions | moves as well |

No conversion of existing data is needed: there are no movements and no saved positions yet. Should an older project
file contain pan/tilt in looks or position presets after all, the import drops them with a warning.

In the move editor, the allowed range of a selected fixture is visible as a frame in the area. Points outside are
marked red.

## Data model (new Flyway migration)

```
figure        (id, project_id, name, closed)
figure_point  (figure_id, position, x, y)                    -- x, y: 0.0–1.0 (= 0–255 DMX, finely resolved)
move          (id, project_id, name, figure_id, size_x, size_y, shift_x, shift_y, rotation_deg,
               duration_ms, repeat_mode, step_offset, once_start)
              -- once_start: sequential | together; figure_id: ON DELETE RESTRICT
move_fixture  (move_id, position, fixture_id, shift_x, shift_y, mirror_x, mirror_y, reverse)
scene_move    (scene_id, section, position, move_id)         -- section as for scene_composition
scene         + move_in_dark, dim_out_ms, travel_ms           -- NULL for the times = project time
project       + dim_out_ms, travel_ms                         -- default 500 / 1500
fixture       + pan_min, pan_max, tilt_min, tilt_max  (NULL = no limit)
party_cell    + move_id
scene_key     + move_id                                       -- a key plays a composition or a move
```

- Deleting a figure a move uses, and a move that is in scenes, party or keys, is rejected ("where used" as for
  compositions).
- If a fixture is deleted from the patch, it drops out of the moves.
- Project files: new version 4 with figures and moves; versions 1–3 stay importable.

## Implementation (sketch)

- **Domain** (`de.vinzens.lumen.domain.move`): `Figure` (validation: ≥ 1 point, values 0–1), `FigurePath`
  (polyline, length, point at distance), `MoveSpec`, `MovePattern implements Pattern` (the sealed interface gets
  another type). A move plays as an `ActiveComposition` (own ID range like `ofLook`, `sourceId` for continuing across
  scenes). Limits on the playback layer, before the programmer is laid over it (`LayerMerger`), tested without Spring.
  Move in dark: `Playback.startTransition` determines the heads that move at GO; `Crossfade` gets a dark phase for
  them (dim out → jump → travel → fade-in), `SceneTiming` the new fields.
- **Application:** `ManageFiguresUseCase`, `ManageMovesUseCase`; scenes, party and keys learn moves. Looks and presets
  reject pan/tilt (validation in `LookSpec` and `PresetSpec`), "Record position" in the programmer.
- **Adapters:** SQLite repositories, REST under `/api/projects/{id}/figures` and `/moves`, DTOs in the web adapter,
  import/export.
- **Frontend:** setup tab **Moves** (list on the left, figure and move editor on the right), choice in
  `SceneCompositions`, `PartyGrid` and the keys. Geometry (path, templates, placement, preview) as pure functions in
  `src/lib/moves.ts` with Vitest, the same rules as in the backend.
- **Tests:** even speed, closed/open figure, offset in loop and once (both starts), ping-pong, mirror, rotation,
  shift per fixture, figure with one point, clamping, two moves on one fixture (LTP), continuing across scenes, head
  without a move stands still, limits in playback, programmer without limits, looks/presets without pan/tilt,
  "Record position" (centre + deviation per fixture), move in dark (lit → move, dark → move, stays, same move keeps
  running, delay, stay time, light markers).

Suggested order, each with a commit:

1. Figures: domain, storage, REST, figure editor with templates
2. Moves: domain with computation, storage, REST, move editor with preview and "Show on stage"
3. Use in scenes and light markers, party and keys; programmer "Record position"; looks and presets without pan/tilt;
   project files version 4
4. Move in dark per scene (cross-fade with dark phase, times in the scene editor and in the show display)
5. Limits per fixture in the patch and in playback (not in the programmer), frame in the XY pad

## Implementation (2026-10-08)

As described above, with these details:

- **Domain:** `domain.move` (`Figure`, `FigurePath`, `MoveSpec`, `MoveFixture`, …); `MovePattern` lives in the package
  `composition`, because the sealed interface `Pattern` only allows types in the same package. Moves play as
  `ActiveComposition.ofMove` with IDs from `ActiveComposition.MOVE_IDS` (2⁴⁰ + move ID).
- **Holding position:** `Playback` remembers pan/tilt of all fixtures at GO and lays the new moves over them. A move
  that fades out stops at GO; the head glides from there to the new move with the fade-in (dark heads jump). So a head
  without a move in the new scene does not keep moving or travel back.
- **Move in dark:** all heads that move in the dark dim in together (after dim out + travel, as soon as one of them was
  lit at GO); a dark head may then wait a little longer. The progress display gets the later start of the fade-in as
  `delay`.
- **Limits:** in DMX values on the fixture (`limits` in the patch API), applied in `OutputEngine` to everything below
  the programmer (playback, party, preview "Show on stage").
- **Existing data:** migration V25 deletes pan/tilt from looks and position presets; the import of older project files
  leaves them out with a warning in the log.
- **Programmer:** "Record position" as a button of its own; "Record scene" optionally puts the position of the heads
  into the new scene as a move (look + position).
- The figure editor is used with the mouse; exact values and reordering go through the point list.

## Later

- Smooth curves instead of straight lines (spline through the points)
- Hold time per point (for journeys: stop, wait, move on)
- Tempo on the beat (BPM, tap) for the party
- Relative to a look position instead of absolute
- Offset "evenly distributed" instead of in steps
- Further channels along the figure (e.g. color depending on the position)
- Move in dark for party fields and keys too
- Use the channel "Blackout while moving" of some fixtures
- Respect the maximum travel speed of a fixture (warning if a figure is too fast)
