# Fixture library (YAML)

The fixture library describes **what a fixture model can do**: channels, modes, value ranges.
It contains **no** project-specific details (address, universe, position, name in the play) – those live in the
project in the SQLite database, see [PROJECT-DATA.md](PROJECT-DATA.md).

```
fixture-types/                   (folder configurable via lumen.fixture-library-dir)
├── generic/
│   ├── generic-dimmer.yml
│   └── generic-rgbw-par.yml
└── examples/
    └── example-moving-head-spot.yml
```

All `*.yml` / `*.yaml` files in the folder and its subfolders are loaded (subfolders e.g. per manufacturer). The `id`
must be unique – a project refers to the fixture type by it.

Lumen picks up changes to the files while running: the folder is checked every 2 s
(`lumen.fixture-library-watch-interval`), and it reloads as soon as the files have not changed for one round. Patch,
output and all browsers use the new profile at once. A broken profile is skipped and shows up as an issue.

---

## Example: LED PAR with two modes

```yaml
id: generic-rgbw-par
manufacturer: Generic
model: LED PAR 7x10W RGBW
category: par                    # par | fresnel | profile | moving-head | scanner | bar | dimmer | strobe | fog | other

modes:
  - name: 4ch
    channels:
      - function: red
      - function: green
      - function: blue
      - function: white

  - name: 8ch
    channels:
      - function: dimmer
      - function: red
      - function: green
      - function: blue
      - function: white
      - function: strobe
        ranges:
          - { from: 0,  to: 9,   label: Off }
          - { from: 10, to: 255, label: Strobe slow → fast }
      - function: program
        default: 0
      - function: generic
        name: Program speed
```

## Example: moving head with 16-bit pan/tilt and color wheel

```yaml
id: moving-head-spot
manufacturer: Example
model: Spot 60
category: moving-head

physical:
  pan-range: 540                 # degrees – for display in the UI
  tilt-range: 270

modes:
  - name: 13ch
    channels:
      - function: pan
        resolution: 16           # uses 2 channels (coarse + fine)
      - function: tilt
        resolution: 16
      - function: speed
        default: 0
      - function: dimmer
      - function: shutter
        default: 255
      - function: color-wheel
        ranges:
          - { from: 0,   to: 9,   label: White, color: "#ffffff" }
          - { from: 10,  to: 19,  label: Red,   color: "#ff0000" }
          - { from: 20,  to: 29,  label: Blue,  color: "#0033ff" }
          - { from: 30,  to: 39,  label: Green, color: "#00ff44" }
      - function: gobo
      - function: gobo-rotation
      - function: focus
      - function: prism
      - function: reset
```

## Example: classic dimmer channel (halogen lantern on a dimmer pack)

```yaml
id: generic-dimmer
manufacturer: Generic
model: Dimmer channel
category: dimmer
modes:
  - name: 1ch
    channels:
      - function: dimmer
```

---

## Supported `function` values

| Category | Values | Behaviour on a scene change ([PLAYBACK.md](PLAYBACK.md)) |
|---|---|---|
| Intensity | `dimmer` | HTP, scaled with the scene level |
| Color | `red`, `green`, `blue`, `white`, `warm-white`, `cold-white`, `amber`, `uv`, `lime`, `cyan`, `magenta`, `yellow`, `cto` | LTP, faded smoothly |
| Position | `pan`, `tilt` | LTP, faded smoothly |
| Beam (continuous) | `focus`, `zoom`, `iris`, `gobo-rotation` | LTP, faded smoothly |
| Discrete | `color-wheel`, `gobo`, `prism`, `shutter`, `strobe`, `program`, `macro`, `reset` | LTP, snaps at GO |
| Other | `speed` | LTP, snaps at GO |
| | `fog`, `fan`, `generic` | LTP, smooth; `generic` needs a `name` |

Optional fields per channel: `name`, `default` (0–255, for 16 bit 0–65535), `resolution` (8/16), `invert` (bool),
`ranges`, `fade: snap|smooth` (overrides the default behaviour).

**Fixtures without a dimmer channel** (e.g. a 4ch RGBW mode) get a **virtual dimmer**: the engine scales the color
channels (`red`, `green`, `blue`, `white`, `warm-white`, `cold-white`, `amber`, `uv`, `lime`), so that they behave
exactly like fixtures with a dimmer in the programmer and when fading. Without a dimmer value a fixture stays dark –
even if colors are set. Subtractive colors (`cyan`, `magenta`, `yellow`) are not scaled.

**Attribute IDs:** every channel controls an attribute whose ID is its `function` (`dimmer`, `red`, …). Generic
channels are told apart by their name: `generic:<name>`, e.g. `generic:Program speed`.

### Multi-pixel fixtures

LED bars with individually controllable segments (pixels) are described by a `heads` block: its channels repeat for
every head, after the channels of the whole fixture (with `first: true` before them). Example:
`fixture-types/generic/generic-led-bar-8-pixel.yml`.

```yaml
modes:
  - name: 26ch
    channels:                    # the whole fixture
      - function: dimmer
      - function: strobe
    heads:
      count: 8                   # 8 segments
      channels:                  # repeats for every segment
        - function: red
        - function: green
        - function: blue
```

Unusual orders (e.g. first all red channels, then all green ones) work with `head: <n>` on the single channel.

- **Attributes per head:** `red@3` is the red of head 3. The attribute without a head (`red`) applies to all heads
  without a value of their own – so the color wheel colors the whole bar; in the programmer a single head can be
  picked under "Heads".
- **Dimmer per head:** if the heads have no dimmer channel of their own, each gets a virtual one (`dimmer@3`). If
  there is a dimmer for the whole fixture, it dims everything, and the head dimmer additionally lowers just its head.
- **Dimmer packs** (only the heads have dimmers, the fixture itself has none, e.g. a 4-channel dimmer pack): there is
  no master. The programmer shows "All channels" and the single channels; a value for the whole fixture (also via the
  API or from a group) is spread onto the channels at once, the programmer and looks only hold channel values.
- **Compositions:** with "Each head separately", a chase, for example, runs across the segments instead of across
  whole fixtures.

### Import from the Open Fixture Library

In the patch view, "Import from Open Fixture Library" takes a fixture from
[open-fixture-library.org](https://open-fixture-library.org): choose *Download → Open Fixture Library JSON* there,
then give the file and the manufacturer (the OFL file does not contain it). Lumen writes a normal YAML profile from it
to `<manufacturer>/<id>.yml` in the library; afterwards it can be edited like any other.

- Capabilities are mapped onto Lumen functions (`Intensity` → `dimmer`, `ColorIntensity` → `red` …, `WheelSlot` →
  `color-wheel`/`gobo` depending on the wheel, `ShutterStrobe` → `shutter` or `strobe`, …). The single ranges become
  `ranges` with label and color.
- Fine channels directly behind their coarse channel become 16 bit, matrix channels (pixels) become heads.
- What Lumen cannot express is simplified and reported as a warning (e.g. a channel with color wheel *and* wheel
  rotation, a fine channel somewhere else, pixel groups). Check the channels before using the fixture on stage.
- If the ID already exists, the dialog asks whether to replace the existing profile.

### Bundled profiles in the Mac app

At start the Mac app copies its profiles to `~/Library/Application Support/Lumen/fixture-types` and remembers in
`.bundled-profiles.properties` which version was installed. On an app update, profiles that were not changed are
replaced by the new version. **Profiles you edited stay**; the new bundled version is put next to them as
`<name>.yml.shipped` (not loaded) for comparison. Deleted profiles do not come back. Installations from before this
feature do not know the old version: a profile that differs there counts as edited and stays.

---

## Validation of the library

- Required fields, unique `id`, known `function`s, values 0–255, colors as `#rrggbb`
- `generic` without `name` → error
- Broken files are skipped instead of aborting the whole start. Problems are available via `GET /api/library/issues`
  and are shown in setup mode.

**Library changes vs. existing projects:** if a project refers to a type/mode that is missing or whose channel count
changed, the patch shows a warning (e.g. "now overlaps with fixture X").
