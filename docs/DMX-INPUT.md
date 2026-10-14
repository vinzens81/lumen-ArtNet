# DMX input (external console)

An external DMX console sends Art-Net (later also sACN) to Lumen. An **input patch** decides which input channel
controls what – the dimmer of a fixture, its color, pan/tilt, the grand master. The console is there for **single
things** next to the Lumen show; it never takes over the whole show.

> **Status:** **proposal, not decided yet** (2026-10-14). Nothing is implemented. The open questions at the end have
> to be answered before the iteration starts (see [ROADMAP.md](ROADMAP.md)).

```
 console ─ Art-Net / sACN ─▶ adapter (artnet / sacn) ─▶ DmxInputPort ─▶ DmxInputService (takeover per target)
                                                                             │
                                  show layers ─▶ LayerMerger ─▶ overrides ◀──┘ ─▶ position limits ─▶ master ─▶ DMX
```

## Basic idea

- DMX itself does not say whether a value was *just set* or *is still set*. Therefore only **changes** from the console
  count: a value that just sits there has no effect at all.
- Every patched input channel is a **target of its own** (one attribute of one fixture, or the grand master). It does
  not matter whether it is intensity, color, pan/tilt or anything else – all targets follow the same rule, and a
  target is never grouped with other attributes of the same fixture.
- **No channel-to-channel pass-through:** a target is always an attribute of a patched fixture (not a DMX address), so
  invert, 16 bit, virtual dimmers and position limits keep working.

## Takeover rule (latest takes precedence)

Per target, either **Lumen** or the **console** is in charge. At the start, Lumen is.

1. **Console takes over** – how, is configurable (see [Takeover mode](#takeover-mode)):
   - **Pickup:** only when the console value **crosses the current Lumen value** (like soft takeover for MIDI
     faders). Nothing jumps: with the scene at 70 % the dimmer follows the console fader once it passes 70 %.
   - **Change:** as soon as the console value moves by at least a **threshold** (default ±5 DMX steps) away from its
     baseline. Quicker to use, but the light jumps to the console value.

   From then on every console value is used.
2. **Lumen takes back:** as soon as **something new and different comes from Lumen itself** for that target, Lumen's
   value is active again at once – no matter where the console stands. The console has to take over again (rule 1);
   in change mode the console value at the moment of the takeback becomes the new baseline, so a fader that just sits
   there does not grab the target straight back.
3. Back and forth as often as needed: Lumen sets a value → the console picks it up and overrides it → Lumen sets
   something new → Lumen is active again → …

**What counts as "new from Lumen":** events that set the target, not the single ticks of a fade or an effect
(otherwise a running rainbow would take a target back 40 times per second):
- GO / back into a scene whose target value for this attribute differs from before
- the programmer setting this attribute
- an action in party mode that sets it (field, gradient, pad, fader)
- for the grand master: changing it in the UI (or via MIDI)

A GO that does not touch the attribute leaves it with the console.

**What counts as a change from the console:**
- The **first frame** of a source (at start, on connect, after a signal loss) is only the baseline, never a change.
  Otherwise switching on the console would override everything.
- 16-bit channels count as one value (the threshold is meant in 8-bit steps of the coarse channel).
- The change threshold also filters fader jitter; with pickup, jitter only matters right at the crossing point.

### Takeover mode

- Set per **input universe** (pickup or change, plus the threshold), with an optional override per **mapping** – e.g.
  pickup for dimmers on faders, change for a color chosen on an encoder.
- Default: open (see open questions); pickup is the safer one in a running show, change the more direct one.

**Signal loss** (no packets for a few seconds): everything the console has goes back to Lumen.

Takeovers live only in memory; after a restart Lumen is in charge of everything.

## Input universes

Per project, a list of input universes, separate from the output universes:

| Field | Meaning |
|---|---|
| Name | e.g. "FOH console" |
| Protocol | Art-Net (step 1), sACN (step 2) |
| Address | Art-Net port address (Net/SubNet/Universe) or sACN universe |
| Network | network interface as in iteration N, or all |
| Source (optional) | accept packets from this IP only |
| Active in | setup / show / party, each can be switched off |
| Takeover | pickup or change, threshold for change (default ±5); can be overridden per mapping |

**No feedback loops:** an input universe with the same address as an output universe is a patch issue. Packets Lumen
sent itself are dropped (relevant above all in broadcast mode, where Lumen receives its own packets).

## Input patch

Kinds of mapping:

1. **Single attribute:** channel *n* (for 16 bit also *n+1*) controls one attribute of one fixture, e.g. `dimmer`,
   `red@head2` or `pan`.
2. **Fixture block** (shortcut): from input channel *n* on, a fixture takes as many channels as its mode, in the same
   order – the console operator patches the same profile at address *n*. This is only a quick way to create single
   attribute mappings: every channel of the block is still a target of its own. Single attributes can be left out.
3. **Grand master:** one channel controls the grand master, with the same takeover and takeback.
4. **Later (step 3):** group intensity, blackout, GO/back (on the edge above 50 %), faders from iteration F, extra keys
   – using the same actions as the [MIDI concept](MIDI.md#actions), so there is only one action model.

**Comfort:**
- **"Mirror output patch":** one click creates a fixture block for every fixture at its output address. The console
  then only has to be patched like Lumen.
- **Learn:** move a fader on the console; Lumen highlights the channel that changes and it can be assigned directly.
- **Input monitor:** the same address grid as in the patch view, with live input values (WebSocket topic `dmxInput`,
  throttled), the mapped ranges and which targets the console currently has.
- **Patch issues:** overlaps, channels beyond 512, a deleted fixture, a changed mode, a missing attribute – shown in
  the existing issue list.

## Visibility

Who is in charge of a value is not visible from the light alone, so overridden values get a mark:

- **Programmer / fixture cards / stage view:** a **"!"** at every value the console has overridden.
- **Show mode (script view):** the script shows no values, so a compact indicator in the header or master bar, e.g.
  "Console: 3", which opens a list of the overridden targets. *(Proposal, still open.)*
- **Party mode:** the same indicator in the master bar; fields and pads touched by an override could show the "!".
  *(Proposal, still open.)*
- **Grand master:** the "!" directly at the grand master fader.

## Lumen as a visible Art-Net node

Many consoles (grandMA, ETC, ChamSys) send unicast only to nodes they found via ArtPoll. Lumen therefore answers
ArtPoll with an **ArtPollReply of its own** that lists its input universes (as output ports from the node's point of
view). Lumen filters itself out of its own node list.

## Architecture

- **Domain** (plain Java):
  - `InputUniverse`, `InputMapping` (sealed: `AttributeChannel`, `FixtureBlock`, `GrandMasterChannel`, later
    `ControlChannel`), `InputPatchValidator`.
  - `InputDecoder`: input frame + mappings + modes → values per target (normalized to 0–1, 16 bit combined).
  - `Takeover`: the state per target (Lumen / console, console baseline, last Lumen value) with both takeover modes
    and the takeback rule as a pure state machine – the core of this feature, tested without Spring.
- **Application:**
  - `DmxInputService` keeps the latest frame per universe with its receive time in an `AtomicReference`, so the
    receive thread never blocks the render loop. Signal loss gets a `Clock`.
  - **Not a normal layer:** console values must not be merged HTP (a console dimmer at 10 % has to beat a scene at
    70 %). They are applied as **overrides after merging the show layers**, before position limits; the programmer
    stays on top in setup mode.
  - "New from Lumen" needs the takeback events: playback (GO/back with the changed target values), programmer and
    party report which fixture attributes they set; the grand master reports its changes.
  - Out-port `DmxInputPort` (listener for received frames), in-ports `ManageInputPatchUseCase`,
    `QueryDmxInputUseCase` and a "give everything back to Lumen" command.
- **Adapters:**
  - `artnet`: the receive socket on 6454 (`ArtNetDiscovery`) additionally parses ArtDmx (`0x5000`) and answers
    ArtPoll.
  - `sacn`: a new receiver that joins the needed multicast groups per interface.
  - Both feed the same port; the adapters stay independent of each other.
- **Persistence:** a new Flyway migration with `input_universe` and `input_mapping`; both travel with the project
  export ([PROJECT-DATA.md](PROJECT-DATA.md)). Takeovers are not stored.
- **Testing without a console:** an `ArtDmxSender` in the tests (like `EspNodeSimulator`); manually with QLC+ as the
  console.
- **Caveat (macOS):** if QLC+ listens on 6454 at the same time, unicast reaches only one of the `SO_REUSEPORT`
  sockets. This should be visible in the status.

## Steps

1. **Art-Net input:** input universes, single attribute, fixture block, grand master, takeover rule (pickup and
   change mode), signal loss, "!" marks, input monitor, ArtPollReply.
   **DoD:** with QLC+ as a console, a dimmer is picked up at the scene value and overridden; the next GO that changes
   it brings the scene value back at once; the grand master works the same way.
2. Learn, "mirror output patch", sACN input.
3. Group intensity and control channels (blackout, GO, faders), aligned with iteration M (MIDI).

## Decisions

- **2026-10-14:** the console is for single things next to the show, never for taking over everything. Only changes
  count (latest takes precedence); Lumen takes a target back at once when it sets something new. Every
  patched channel is a target of its own, regardless of the attribute. The grand master can be mapped too.
  Takeover is configurable: pickup (cross the Lumen value) or change (move by a threshold, default ±5 DMX steps).

## Open questions

1. **Show and party mode:** how exactly are overrides shown there (indicator, list, "give everything back")?
2. Should a takeback by a GO use the scene's **fade** from the console value, or switch at once to the value Lumen
   computes (with a running fade that is Lumen's own fade value)?
3. Is **Art-Net** enough to start with, or is sACN input needed right away?
4. Should DMX input also work in **party mode**, or only in setup and show?
5. **Default takeover mode:** pickup or change? And is the override per mapping needed, or is per input universe
   enough?
