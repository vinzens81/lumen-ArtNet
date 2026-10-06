<script lang="ts">
  import {
    attributeOf, baseOf, defaultValue, dimmersOnlyPerHead, headDimmers, headOf, onlyHeadDimmers, supportedValues,
    valueFor, withHead,
  } from '../lib/attributes';
  import { hsvToRgb, rgbToCss, rgbToHsv, type Rgb } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import type { ProgrammerEntry } from '../lib/programmer';
  import type { AttributeValues, Channel } from '../lib/types';
  import ColorWheel from './ColorWheel.svelte';
  import Fader from './Fader.svelte';
  import PresetPanel from './PresetPanel.svelte';
  import XYPad from './XYPad.svelte';

  let {
    projectId,
    entries,
    selection,
    compact = false,
    onclose,
  }: {
    projectId: number | null;
    /** all patched fixtures (presets need their modes) */
    entries: ProgrammerEntry[];
    /** ids of the fixtures the controls set */
    selection: number[];
    /** one column, e.g. in a small overlay */
    compact?: boolean;
    /** shows a close button */
    onclose?: () => void;
  } = $props();

  const RGB = ['red', 'green', 'blue'];
  const EXTRA_COLORS = ['white', 'warm-white', 'cold-white', 'amber', 'lime', 'uv', 'cyan', 'magenta', 'yellow', 'cto'];
  const HANDLED = new Set(['dimmer', ...RGB, ...EXTRA_COLORS, 'color-wheel', 'pan', 'tilt']);
  const QUICK_COLORS: { name: string; rgb: Rgb }[] = [
    { name: 'Red', rgb: [1, 0, 0] },
    { name: 'Orange', rgb: [1, 0.45, 0] },
    { name: 'Yellow', rgb: [1, 0.85, 0] },
    { name: 'Green', rgb: [0, 1, 0.1] },
    { name: 'Cyan', rgb: [0, 0.9, 1] },
    { name: 'Blue', rgb: [0, 0.2, 1] },
    { name: 'Magenta', rgb: [1, 0, 0.8] },
    { name: 'Warm', rgb: [1, 0.72, 0.38] },
    { name: 'White', rgb: [1, 1, 1] },
  ];

  let showRaw = $state(false);

  const selected = $derived(entries.filter((e) => selection.includes(e.fixture.id)));
  /** heads of the selected multi-pixel fixtures; 0 if none has heads */
  const headCount = $derived(Math.max(0, ...selected.map((e) => e.mode.heads)));
  /** which head the controls set: 0 = the whole fixture (all heads), 1… = one head */
  let headScope = $state(0);
  $effect(() => {
    if (headScope > headCount) headScope = 0;
  });
  // other fixtures start with all heads again, so a head chosen before does not stick by accident
  const selectionKey = $derived(selection.join(','));
  $effect(() => {
    void selectionKey;
    headScope = 0;
  });
  /** attributes the controls offer, without head: of the whole fixture, or of the chosen head */
  const available = $derived(new Set(selected.flatMap((e) => e.attributes)
    .filter((a) => headOf(a) === headScope)
    .map(baseOf)));
  const modes = $derived(new Map(entries.map((e) => [e.fixture.id, e.mode])));

  function valueOf(entry: ProgrammerEntry, attribute: string): number {
    return valueFor(live.programmer[entry.fixture.id] ?? {}, attribute) ?? defaultValue(entry.mode, attribute);
  }

  /**
   * Dimmer packs (only the heads have dimmers): their channels are shown and set directly, "All channels" sets each
   * of them – there is no master to set.
   */
  const channelMode = $derived(selected.length > 0 && selected.every((e) => dimmersOnlyPerHead(e.mode)));
  const channels = $derived([...new Set(selected.flatMap((e) => headDimmers(e.mode)))].sort((a, b) => a - b));
  /** choosing a head would offer nothing the channel faders do not */
  const showHeads = $derived(headCount > 0 && !(channelMode && selected.every((e) => onlyHeadDimmers(e.mode))));

  function reference(attribute: string): { value: number; mixed: boolean } {
    return referenceOf([withHead(attribute, headScope)]);
  }

  /** The value the attributes share across the selection, or the first one and `mixed`. */
  function referenceOf(attributes: string[]): { value: number; mixed: boolean } {
    const values = selected.flatMap((e) => attributes.filter((a) => e.attributes.includes(a)).map((a) => valueOf(e, a)));
    if (values.length === 0) return { value: 0, mixed: false };
    return { value: values[0], mixed: values.some((v) => Math.abs(v - values[0]) > 0.002) };
  }

  /** Sets the channels of the selected dimmer packs; `heads` empty = every channel. */
  function setChannels(level: number, heads: number[] = []) {
    const perFixture: Record<number, AttributeValues> = {};
    for (const entry of selected) {
      const own = headDimmers(entry.mode).filter((h) => heads.length === 0 || heads.includes(h));
      if (own.length > 0) perFixture[entry.fixture.id] = Object.fromEntries(own.map((h) => [`dimmer@${h}`, level]));
    }
    if (Object.keys(perFixture).length > 0) live.setValues(perFixture);
  }

  function set(values: AttributeValues) {
    const scoped = Object.fromEntries(Object.entries(values).map(([a, v]) => [withHead(a, headScope), v]));
    const perFixture: Record<number, AttributeValues> = {};
    for (const entry of selected) {
      const supported = supportedValues(entry.mode, scoped);
      if (Object.keys(supported).length > 0) perFixture[entry.fixture.id] = supported;
    }
    if (Object.keys(perFixture).length > 0) live.setValues(perFixture);
  }

  const hasRgb = $derived(RGB.every((c) => available.has(c)));
  const currentHsv = $derived.by(() => {
    const entry = selected.find((e) => RGB.every((c) => e.attributes.includes(withHead(c, headScope))));
    if (!entry) return [0, 0, 1];
    return rgbToHsv(RGB.map((c) => valueOf(entry, withHead(c, headScope))) as Rgb);
  });

  function setColor(rgb: Rgb) {
    set({ red: rgb[0], green: rgb[1], blue: rgb[2] });
  }

  function firstChannel(attribute: string): Channel | undefined {
    for (const entry of selected) {
      const channel = entry.mode.channels.find((c) => baseOf(attributeOf(c)) === attribute);
      if (channel) return channel;
    }
    return undefined;
  }

  const otherAttributes = $derived(
    [...available].filter((a) => !HANDLED.has(a)).sort((a, b) => a.localeCompare(b)),
  );

  function label(attribute: string): string {
    const name = attribute.startsWith('generic:') ? attribute.slice(8) : attribute.replace('-', ' ');
    return name.charAt(0).toUpperCase() + name.slice(1);
  }
</script>

{#if selected.length > 0}
<header class="panel-head">
  <div>
    <h2>{selected.length === 1 ? selected[0].fixture.name : `${selected.length} fixtures`}</h2>
    <span class="dim small">{selected.length === 1 ? `${selected[0].type.model} · ${selected[0].mode.name}` : 'Values apply to all selected fixtures'}</span>
  </div>
  <div class="head-actions">
    <button class="btn small ghost" type="button" onclick={() => live.clear(selection)}>Clear</button>
    {#if onclose}
      <button class="btn small ghost" type="button" onclick={onclose} aria-label="Close">✕</button>
    {/if}
  </div>
</header>

<div class="controls" class:compact>

{#if showHeads}
  <section>
    <h3>Heads</h3>
    <div class="heads" role="radiogroup" aria-label="Heads the controls set">
      <button type="button" class="btn small" class:active={headScope === 0} role="radio"
        aria-checked={headScope === 0} onclick={() => (headScope = 0)}>All</button>
      {#each Array.from({ length: headCount }, (_, i) => i + 1) as head (head)}
        <button type="button" class="btn small" class:active={headScope === head} role="radio"
          aria-checked={headScope === head} onclick={() => (headScope = head)}>{head}</button>
      {/each}
    </div>
    <p class="hint dim">
      {headScope === 0 ? 'Values apply to all heads that have no value of their own.' : `Values apply to head ${headScope} only.`}
    </p>
  </section>
{/if}

{#if projectId !== null}
  <PresetPanel {projectId} {selection} attributes={available}
    {modes} />
{/if}

<section>
  <h3>Intensity</h3>
  {#if channelMode}
    {@const all = referenceOf(channels.map((h) => `dimmer@${h}`))}
    <Fader label="All channels" value={all.value} mixed={all.mixed} onchange={(v) => setChannels(v)} />
    <div class="quick">
      {#each [0, 0.25, 0.5, 0.75, 1] as level (level)}
        <button type="button" class="btn small" onclick={() => setChannels(level)}>{level * 100}%</button>
      {/each}
    </div>
    {#each channels as head (head)}
      {@const own = referenceOf([`dimmer@${head}`])}
      <Fader label="Channel {head}" value={own.value} mixed={own.mixed} onchange={(v) => setChannels(v, [head])} />
    {/each}
    <p class="hint dim">The channels are set one by one – the fixture has no master dimmer.</p>
  {:else}
    <Fader label="Dimmer" value={reference('dimmer').value} mixed={reference('dimmer').mixed}
      onchange={(v) => set({ dimmer: v })} />
    <div class="quick">
      {#each [0, 0.25, 0.5, 0.75, 1] as level (level)}
        <button type="button" class="btn small" onclick={() => set({ dimmer: level })}>{level * 100}%</button>
      {/each}
    </div>
  {/if}
</section>

{#if hasRgb}
  <section>
    <h3>Color</h3>
    <ColorWheel hue={currentHsv[0]} saturation={currentHsv[1]} onchange={(h, s) => setColor(hsvToRgb(h, s, 1))} />
    <div class="presets">
      {#each QUICK_COLORS as preset (preset.name)}
        <button type="button" class="preset" title={preset.name} aria-label={preset.name}
          style="--color: {rgbToCss(preset.rgb)}" onclick={() => setColor(preset.rgb)}></button>
      {/each}
    </div>
  </section>
{/if}

{#if EXTRA_COLORS.some((c) => available.has(c))}
  <section>
    {#each EXTRA_COLORS.filter((c) => available.has(c)) as attribute (attribute)}
      <Fader label={label(attribute)} value={reference(attribute).value} mixed={reference(attribute).mixed}
        color={attribute === 'uv' ? '#8a4dff' : attribute === 'amber' ? '#ffab2e' : '#f3f6ff'}
        onchange={(v) => set({ [attribute]: v })} />
    {/each}
  </section>
{/if}

{#if available.has('color-wheel')}
  {@const wheel = firstChannel('color-wheel')}
  <section>
    <h3>Color wheel</h3>
    <div class="slots">
      {#each wheel?.ranges ?? [] as range (range.from)}
        <button type="button" class="slot" onclick={() => set({ 'color-wheel': (range.from + range.to) / 2 / 255 })}
          class:active={Math.round(reference('color-wheel').value * 255) >= range.from && Math.round(reference('color-wheel').value * 255) <= range.to}>
          <span class="slot-color" style="--color: {range.color ?? 'transparent'}" class:none={!range.color}></span>
          {range.label}
        </button>
      {/each}
    </div>
  </section>
{/if}

{#if available.has('pan') && available.has('tilt')}
  <section>
    <h3>Position</h3>
    <XYPad x={reference('pan').value} y={reference('tilt').value} onchange={(x, y) => set({ pan: x, tilt: y })} />
  </section>
{/if}

{#if otherAttributes.length > 0}
  <section>
    <h3>Beam & more</h3>
    {#each otherAttributes as attribute (attribute)}
      {@const channel = firstChannel(attribute)}
      <div class="other">
        <Fader label={label(attribute)} value={reference(attribute).value} mixed={reference(attribute).mixed}
          onchange={(v) => set({ [attribute]: v })} />
        {#if channel && channel.ranges.length > 0}
          <div class="ranges">
            {#each channel.ranges as range (range.from)}
              <button type="button" class="btn small" onclick={() => set({ [attribute]: (range.from + range.to) / 2 / 255 })}>{range.label}</button>
            {/each}
          </div>
        {/if}
      </div>
    {/each}
  </section>
{/if}

{#if selected.length === 1}
  {@const entry = selected[0]}
  <section>
    <button type="button" class="raw-toggle" onclick={() => (showRaw = !showRaw)} aria-expanded={showRaw}>
      {showRaw ? '▾' : '▸'} Raw DMX channels
    </button>
    {#if showRaw}
      <table class="raw mono">
        <tbody>
          {#each entry.mode.channels as channel (channel.offset)}
            {@const value = valueOf(entry, attributeOf(channel))}
            {@const max = channel.resolution === 16 ? 65535 : 255}
            <tr>
              <td class="dim">{entry.fixture.address + channel.offset - 1}</td>
              <td>{channel.name}</td>
              <td class="num">{Math.round(value * max)}{channel.resolution === 16 ? ' (16 bit)' : ''}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </section>
{/if}
</div>
{/if}

<style>
  .heads {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .heads .active {
    border-color: var(--accent);
    color: var(--accent);
  }

  .panel-head {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  /* sections side by side when there is room, so a complex fixture fits without endless scrolling */
  .controls {
    columns: 300px;
    column-gap: 20px;
  }

  .controls.compact {
    columns: auto;
  }

  .controls > section {
    break-inside: avoid;
    margin-bottom: 14px;
  }

  .controls > :global(*) {
    break-inside: avoid;
  }

  .small {
    font-size: 11px;
  }

  section {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .quick,
  .ranges {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .presets {
    display: grid;
    grid-template-columns: repeat(9, 1fr);
    gap: 6px;
  }

  .preset {
    aspect-ratio: 1;
    border-radius: 50%;
    border: 2px solid rgba(255, 255, 255, 0.15);
    background: var(--color);
    box-shadow: 0 0 10px var(--color);
    cursor: pointer;
  }

  .preset:hover {
    border-color: #fff;
  }

  .slots {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;
  }

  .slot {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .slot.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .slot-color {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--color);
    box-shadow: 0 0 8px var(--color);
  }

  .slot-color.none {
    border: 1px dashed var(--text-faint);
  }

  .other {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .raw-toggle {
    padding: 0;
    border: 0;
    background: none;
    color: var(--text-dim);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .raw {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
  }

  .raw td {
    padding: 4px 6px;
    border-bottom: 1px solid var(--border);
  }

  .raw .num {
    text-align: right;
  }

  .hint {
    margin: 0;
    font-size: 11px;
  }

  .head-actions {
    display: flex;
    align-items: flex-start;
    gap: 4px;
  }
</style>
