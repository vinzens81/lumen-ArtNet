<script lang="ts">
  import ColorWheel from '../components/ColorWheel.svelte';
  import Fader from '../components/Fader.svelte';
  import FixtureIcon from '../components/FixtureIcon.svelte';
  import Modal from '../components/Modal.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import XYPad from '../components/XYPad.svelte';
  import { api } from '../lib/api';
  import { attributeOf, attributesOf, defaultValue, supportedValues } from '../lib/attributes';
  import { hexToRgb, hsvToRgb, previewOf, rgbToCss, rgbToHsv, type Rgb } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { programmerSelection } from '../lib/selection.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { AttributeValues, Channel, Fixture, FixtureType, Group, Mode, Universe } from '../lib/types';

  interface Entry {
    fixture: Fixture;
    type: FixtureType;
    mode: Mode;
    attributes: string[];
  }

  const RGB = ['red', 'green', 'blue'];
  const EXTRA_COLORS = ['white', 'warm-white', 'cold-white', 'amber', 'lime', 'uv', 'cyan', 'magenta', 'yellow', 'cto'];
  const HANDLED = new Set(['dimmer', ...RGB, ...EXTRA_COLORS, 'color-wheel', 'pan', 'tilt']);
  const PRESETS: { name: string; rgb: Rgb }[] = [
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

  let universes = $state<Universe[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let showRaw = $state(false);
  let groupOpen = $state(false);
  let editingGroup = $state<Group | null>(null);
  let groupForm = $state({ name: '', color: '#3cf0ff', useSelection: true });
  let recordOpen = $state(false);

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(projectId: number) {
    try {
      [universes, fixtures, groups] = await Promise.all([
        api.universes.list(projectId),
        api.fixtures.list(projectId),
        api.groups.list(projectId),
      ]);
      select(selection.filter((id) => fixtures.some((f) => f.id === id)));
    } catch (e) {
      showError(e);
    }
  }

  const entries = $derived.by(() => {
    const result: Entry[] = [];
    for (const fixture of fixtures) {
      const type = app.fixtureType(fixture.fixtureTypeId);
      const mode = type?.modes.find((m) => m.name === fixture.mode);
      if (type && mode) result.push({ fixture, type, mode, attributes: attributesOf(mode) });
    }
    return result.sort((a, b) => a.fixture.universeId - b.fixture.universeId || a.fixture.address - b.fixture.address);
  });

  // shared with the scene editor, which can merge only the selected fixtures
  const selection = $derived(programmerSelection.ids);
  const selected = $derived(entries.filter((e) => selection.includes(e.fixture.id)));
  const available = $derived(new Set(selected.flatMap((e) => e.attributes)));
  const universeNumber = $derived(new Map(universes.map((u) => [u.id, u.number])));
  const masterLevel = $derived(live.master.blackout ? 0 : live.master.level);

  // --- values ------------------------------------------------------------------------------------------------

  function valueOf(entry: Entry, attribute: string): number {
    return live.programmer[entry.fixture.id]?.[attribute] ?? defaultValue(entry.mode, attribute);
  }

  function reference(attribute: string): { value: number; mixed: boolean } {
    const withAttribute = selected.filter((e) => e.attributes.includes(attribute));
    if (withAttribute.length === 0) return { value: 0, mixed: false };
    const values = withAttribute.map((e) => valueOf(e, attribute));
    return { value: values[0], mixed: values.some((v) => Math.abs(v - values[0]) > 0.002) };
  }

  function set(values: AttributeValues) {
    const perFixture: Record<number, AttributeValues> = {};
    for (const entry of selected) {
      const supported = supportedValues(entry.mode, values);
      if (Object.keys(supported).length > 0) perFixture[entry.fixture.id] = supported;
    }
    if (Object.keys(perFixture).length > 0) live.setValues(perFixture);
  }

  const hasRgb = $derived(RGB.every((c) => available.has(c)));
  const currentHsv = $derived.by(() => {
    const entry = selected.find((e) => RGB.every((c) => e.attributes.includes(c)));
    if (!entry) return [0, 0, 1];
    return rgbToHsv(RGB.map((c) => valueOf(entry, c)) as Rgb);
  });

  function setColor(rgb: Rgb) {
    set({ red: rgb[0], green: rgb[1], blue: rgb[2] });
  }

  function firstChannel(attribute: string): Channel | undefined {
    for (const entry of selected) {
      const channel = entry.mode.channels.find((c) => attributeOf(c) === attribute);
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

  // --- selection ---------------------------------------------------------------------------------------------

  function select(ids: number[]) {
    programmerSelection.ids = ids;
  }

  function toggle(id: number, event: MouseEvent) {
    if (event.shiftKey || event.metaKey || event.ctrlKey) {
      select(selection.includes(id) ? selection.filter((s) => s !== id) : [...selection, id]);
    } else {
      select(selection.length === 1 && selection[0] === id ? [] : [id]);
    }
  }

  function selectGroup(group: Group) {
    select(group.fixtureIds.filter((id) => entries.some((e) => e.fixture.id === id)));
  }

  function preview(entry: Entry) {
    const p = previewOf(entry.mode, live.programmer[entry.fixture.id] ?? {}, masterLevel);
    return `--glow: ${rgbToCss(p.color)}; --intensity: ${p.intensity.toFixed(3)}`;
  }

  // --- groups ------------------------------------------------------------------------------------------------

  function openGroup(group: Group | null) {
    editingGroup = group;
    groupForm = { name: group?.name ?? '', color: group?.color ?? '#3cf0ff', useSelection: group === null };
    groupOpen = true;
  }

  async function saveGroup() {
    if (!project) return;
    const request = {
      name: groupForm.name,
      color: groupForm.color,
      sortOrder: editingGroup?.sortOrder ?? groups.length,
      fixtureIds: groupForm.useSelection ? selection : (editingGroup?.fixtureIds ?? []),
    };
    try {
      if (editingGroup) await api.groups.update(project.id, editingGroup.id, request);
      else await api.groups.create(project.id, request);
      notify(`Saved group "${request.name}"`);
      groupOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function deleteGroup() {
    if (!project || !editingGroup) return;
    try {
      await api.groups.remove(project.id, editingGroup.id);
      groupOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  function onkeydown(event: KeyboardEvent) {
    if (event.key === 'Escape' && !groupOpen) select([]);
  }
</script>

<svelte:window {onkeydown} />

{#if !project}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else if entries.length === 0}
  <div class="glass empty">
    <h2>Nothing to control yet</h2>
    <p>Patch some fixtures first.</p>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/patch')}>Go to patch</button>
  </div>
{:else}
  <div class="programmer">
    <!-- Groups ------------------------------------------------------------------------------------------ -->
    <aside class="groups glass">
      <h3>Groups</h3>
      <button type="button" class="group" onclick={() => select(entries.map((e) => e.fixture.id))}>
        <span class="swatch all"></span>All fixtures<span class="count mono">{entries.length}</span>
      </button>
      {#each groups as group (group.id)}
        <div class="group-row">
          <button type="button" class="group" onclick={() => selectGroup(group)}>
            <span class="swatch" style="--color: {group.color ?? '#8a93a6'}"></span>
            {group.name}<span class="count mono">{group.fixtureIds.length}</span>
          </button>
          <button type="button" class="btn ghost small edit" onclick={() => openGroup(group)} aria-label="Edit {group.name}">✎</button>
        </div>
      {/each}
      <button type="button" class="btn small" disabled={selection.length === 0} onclick={() => openGroup(null)}>
        + Group from selection
      </button>
      <p class="hint dim">Click selects one fixture, Shift/⌘-click adds more. Esc clears the selection.</p>
    </aside>

    <!-- Fixture cards ----------------------------------------------------------------------------------- -->
    <div class="center">
    <div class="toolbar">
      <span class="dim">{Object.keys(live.programmer).length} fixtures with programmer values</span>
      <button class="btn small ghost" type="button" onclick={() => live.clear(Object.keys(live.programmer).map(Number))}
        disabled={Object.keys(live.programmer).length === 0}>Clear programmer</button>
      <button class="btn small record" type="button" onclick={() => (recordOpen = true)}
        disabled={Object.keys(live.programmer).length === 0}>⏺ Record scene</button>
    </div>
    <section class="cards">
      {#each entries as entry (entry.fixture.id)}
        <button
          type="button"
          class="card glass"
          class:selected={selection.includes(entry.fixture.id)}
          style={preview(entry)}
          onclick={(e) => toggle(entry.fixture.id, e)}
          aria-pressed={selection.includes(entry.fixture.id)}
        >
          <span class="lamp"><FixtureIcon category={entry.type.category} size={22} /></span>
          <span class="card-name">{entry.fixture.name}</span>
          <span class="card-meta mono">{universeNumber.get(entry.fixture.universeId)}·{entry.fixture.address}</span>
          <span class="card-level mono">{Math.round((live.programmer[entry.fixture.id]?.dimmer ?? 0) * 100)}%</span>
        </button>
      {/each}
    </section>
    </div>

    <!-- Programmer -------------------------------------------------------------------------------------- -->
    <aside class="panel glass">
      {#if selected.length === 0}
        <div class="none">
          <h3>Programmer</h3>
          <p class="dim">Select fixtures to control them. When the look is right, record it as a scene.</p>
        </div>
      {:else}
        <header class="panel-head">
          <div>
            <h2>{selected.length === 1 ? selected[0].fixture.name : `${selected.length} fixtures`}</h2>
            <span class="dim small">{selected.length === 1 ? `${selected[0].type.model} · ${selected[0].mode.name}` : 'Values apply to all selected fixtures'}</span>
          </div>
          <button class="btn small ghost" type="button" onclick={() => live.clear(selection)}>Clear</button>
        </header>

        <section>
          <h3>Intensity</h3>
          <Fader label="Dimmer" value={reference('dimmer').value} mixed={reference('dimmer').mixed}
            onchange={(v) => set({ dimmer: v })} />
          <div class="quick">
            {#each [0, 0.25, 0.5, 0.75, 1] as level (level)}
              <button type="button" class="btn small" onclick={() => set({ dimmer: level })}>{level * 100}%</button>
            {/each}
          </div>
        </section>

        {#if hasRgb}
          <section>
            <h3>Color</h3>
            <ColorWheel hue={currentHsv[0]} saturation={currentHsv[1]} onchange={(h, s) => setColor(hsvToRgb(h, s, 1))} />
            <div class="presets">
              {#each PRESETS as preset (preset.name)}
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
      {/if}
    </aside>
  </div>
{/if}

<RecordSceneModal bind:open={recordOpen} />

<Modal bind:open={groupOpen} title={editingGroup ? 'Edit group' : 'New group'}>
  <form id="group-form" onsubmit={(e) => { e.preventDefault(); saveGroup(); }}>
    <div class="row">
      <label class="field">Name<input bind:value={groupForm.name} placeholder="Front light" required /></label>
      <label class="field color-field">Color<input type="color" bind:value={groupForm.color} /></label>
    </div>
    <label class="check">
      <input type="checkbox" bind:checked={groupForm.useSelection} disabled={!editingGroup} />
      Use the current selection as members ({selection.length} fixtures)
    </label>
  </form>
  {#snippet actions()}
    {#if editingGroup}<button class="btn danger" type="button" onclick={deleteGroup}>Delete</button>{/if}
    <button class="btn ghost" type="button" onclick={() => (groupOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="group-form">Save</button>
  {/snippet}
</Modal>

<style>
  .programmer {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr) 340px;
    gap: 12px;
    align-items: start;
  }

  .groups,
  .panel {
    position: sticky;
    top: 84px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
    padding: 16px;
    max-height: calc(100vh - 170px);
    overflow: auto;
  }

  .group-row {
    display: flex;
    align-items: center;
  }

  .group {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .group:hover {
    border-color: var(--border-strong);
    background: var(--panel-raised);
  }

  .swatch {
    width: 12px;
    height: 12px;
    border-radius: 4px;
    background: var(--color);
    box-shadow: 0 0 8px var(--color);
  }

  .swatch.all {
    background: conic-gradient(red, yellow, lime, cyan, blue, magenta, red);
  }

  .count {
    margin-left: auto;
    font-size: 11px;
    color: var(--text-faint);
  }

  .edit {
    opacity: 0.5;
  }

  .hint {
    margin: 4px 0 0;
    font-size: 11px;
  }

  .center {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .toolbar .dim {
    flex: 1;
    font-size: 12px;
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
    gap: 12px;
    min-width: 0;
  }

  .card {
    position: relative;
    display: grid;
    grid-template-columns: auto 1fr;
    grid-template-rows: auto auto;
    gap: 2px 12px;
    align-items: center;
    padding: 14px;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
    overflow: hidden;
    transition: border-color 0.15s, box-shadow 0.25s;
    box-shadow:
      var(--shadow),
      0 0 calc(40px * var(--intensity)) color-mix(in srgb, var(--glow) calc(var(--intensity) * 70%), transparent);
  }

  .card::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(120% 90% at 0% 0%, var(--glow), transparent 70%);
    opacity: calc(var(--intensity) * 0.35);
    pointer-events: none;
  }

  .card.selected {
    border-color: var(--accent);
    outline: 2px solid var(--accent);
    outline-offset: -1px;
  }

  .lamp {
    grid-row: span 2;
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    border-radius: 50%;
    color: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), var(--text-faint));
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 45%), rgba(255, 255, 255, 0.04));
    box-shadow: 0 0 calc(24px * var(--intensity)) var(--glow);
  }

  .card-name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .card-meta {
    font-size: 11px;
    color: var(--text-faint);
  }

  .card-level {
    position: absolute;
    right: 12px;
    bottom: 10px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .none {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .none p {
    margin: 0;
  }

  .panel-head {
    display: flex;
    justify-content: space-between;
    gap: 8px;
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

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .color-field {
    flex: 0 0 90px;
  }

  .color-field input {
    padding: 4px;
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  @media (max-width: 1200px) {
    .programmer {
      grid-template-columns: minmax(0, 1fr) 320px;
    }

    .groups {
      grid-column: 1 / -1;
      position: static;
      flex-direction: row;
      flex-wrap: wrap;
      align-items: center;
      max-height: none;
    }

    .hint {
      display: none;
    }
  }

  @media (max-width: 800px) {
    .programmer {
      grid-template-columns: minmax(0, 1fr);
    }

    .panel {
      position: static;
      max-height: none;
    }
  }
</style>
