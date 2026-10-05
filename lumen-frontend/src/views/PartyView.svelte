<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { beatAt, buttonKey, commonIntensity, commonPalette, selectedFixtures } from '../lib/party';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import type { Fixture, Group, PartyButtonState, PartyStatus } from '../lib/types';

  const INTENSITY_SEND_MS = 50;

  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedGroups = $state(new Set<number>());
  let allSelected = $state(false);
  let now = $state(performance.now());
  /** buttons held down by pointer or key, to release them exactly once */
  const held = new Set<number>();

  const projectId = $derived(app.active?.id ?? null);
  const status = $derived(live.party);
  const selection = $derived(selectedFixtures(groups, selectedGroups, allSelected, fixtures.map((f) => f.id)));
  const selectionIntensity = $derived(commonIntensity(selection, status));
  const selectionPalette = $derived(commonPalette(selection, status));
  const beat = $derived(status ? beatAt(status.msSinceBeat, status.beatMs, now - live.partyReceivedAt) : null);
  const paletteColors = $derived(new Map((status?.palettes ?? []).map((p) => [p.id, p.color])));

  let bpmInput = $state(120);
  $effect(() => {
    if (status) bpmInput = status.bpm;
  });

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  onMount(() => {
    api.party.status().then((s) => live.updateParty(s)).catch(showError);
    let frame = requestAnimationFrame(function tick() {
      now = performance.now();
      frame = requestAnimationFrame(tick);
    });
    const releaseWakeLock = wakeLock.hold();
    return () => {
      cancelAnimationFrame(frame);
      releaseWakeLock();
      held.forEach((id) => api.party.release(id).catch(() => {}));
    };
  });

  async function load(id: number) {
    try {
      [fixtures, groups] = await Promise.all([api.fixtures.list(id), api.groups.list(id)]);
    } catch (e) {
      showError(e);
    }
  }

  async function run(request: Promise<PartyStatus>) {
    try {
      live.updateParty(await request);
    } catch (e) {
      showError(e);
    }
  }

  function toggleGroup(id: number) {
    allSelected = false;
    const next = new Set(selectedGroups);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    selectedGroups = next;
  }

  function toggleAll() {
    allSelected = !allSelected;
    selectedGroups = new Set();
  }

  function setPalette(palette: string) {
    if (selection.length > 0) run(api.party.palette(selection, palette));
  }

  // fader moves are sent at most every INTENSITY_SEND_MS
  let pendingLevel: number | null = null;
  let levelTimer: ReturnType<typeof setTimeout> | null = null;
  function setIntensity(level: number) {
    if (selection.length === 0) return;
    pendingLevel = level;
    if (levelTimer !== null) return;
    const ids = selection;
    levelTimer = setTimeout(() => {
      levelTimer = null;
      if (pendingLevel !== null) run(api.party.intensity(ids, pendingLevel));
      pendingLevel = null;
    }, INTENSITY_SEND_MS);
  }

  function setBpm() {
    if (Number.isFinite(bpmInput)) run(api.party.setBpm(bpmInput));
  }

  function press(button: PartyButtonState) {
    if (held.has(button.id)) return;
    if (button.mode === 'flash') held.add(button.id);
    run(api.party.press(button.id));
  }

  function release(button: PartyButtonState) {
    if (!held.delete(button.id)) return;
    run(api.party.release(button.id));
  }

  function fixtureGlow(fixture: Fixture): string {
    const state = status?.fixtures[fixture.id];
    const color = (state?.palette && paletteColors.get(state.palette)) || '#ffffff';
    return `--color: ${color}; --intensity: ${state?.intensity ?? 0}`;
  }

  function onkeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('input, textarea, select, [contenteditable]') || event.metaKey || event.ctrlKey) return;
    if (event.key === 't' || event.key === 'T') {
      event.preventDefault();
      if (!event.repeat) run(api.party.tap());
      return;
    }
    const index = Number(event.key) - 1;
    const button = status?.buttons[index];
    if (button && buttonKey(index) === event.key && !event.repeat) {
      event.preventDefault();
      press(button);
    }
  }

  function onkeyup(event: KeyboardEvent) {
    const index = Number(event.key) - 1;
    const button = status?.buttons[index];
    if (button && buttonKey(index) === event.key) release(button);
  }
</script>

<svelte:window {onkeydown} {onkeyup} />

{#if !app.active}
  <div class="glass empty"><h2>No active project</h2></div>
{:else}
  <section class="party">
    <header class="tempo glass">
      <div class="bpm">
        <span class="value mono">{status ? Math.round(status.bpm) : '–'}</span>
        <span class="unit">BPM</span>
      </div>
      <div class="beats" aria-hidden="true">
        {#each [0, 1, 2, 3] as i (i)}
          <span class="beat" class:on={beat?.beat === i}
            style="--pulse: {beat?.beat === i ? 1 - beat.phase : 0}"></span>
        {/each}
      </div>
      <button class="btn tap" type="button" onclick={() => run(api.party.tap())}
        title="Tap along with the music (key T)">TAP <kbd>T</kbd></button>
      <form class="bpm-form" onsubmit={(e) => { e.preventDefault(); setBpm(); }}>
        <label>Set <input type="number" min="40" max="240" step="1" bind:value={bpmInput} /></label>
      </form>
      <span class="spacer"></span>
      <span class="badge experimental" title="Party mode is an early experiment and will change">Experimental</span>
      <button class="btn" type="button" onclick={() => run(api.party.clear())}
        title="Colors, intensities and buttons off">Clear all</button>
    </header>

    <div class="layout">
      <aside class="groups glass">
        <h3>Groups</h3>
        <button type="button" class="chip" class:selected={allSelected} aria-pressed={allSelected}
          onclick={toggleAll}>All fixtures</button>
        {#each groups as group (group.id)}
          <button type="button" class="chip" class:selected={selectedGroups.has(group.id)}
            aria-pressed={selectedGroups.has(group.id)} onclick={() => toggleGroup(group.id)}
            style={group.color ? `--group: ${group.color}` : ''}>
            {group.name}<span class="count">{group.fixtureIds.length}</span>
          </button>
        {:else}
          <p class="dim small">No groups yet – create them in the programmer, or use “All fixtures”.</p>
        {/each}
        <p class="dim small selection">
          {selection.length === 0 ? 'Select groups to give them color' : `${selection.length} fixtures selected`}
        </p>
      </aside>

      <div class="center">
        <section class="glass panel">
          <h3>Color</h3>
          <div class="palettes">
            {#each status?.palettes ?? [] as palette (palette.id)}
              <button type="button" class="palette" class:current={selectionPalette === palette.id}
                style="--swatch: {palette.color}" disabled={selection.length === 0}
                onclick={() => setPalette(palette.id)}>
                <span class="swatch"></span>{palette.label}
              </button>
            {/each}
          </div>
        </section>

        <section class="glass panel">
          <h3>Intensity</h3>
          <div class="intensity">
            <button class="btn" type="button" disabled={selection.length === 0}
              onclick={() => run(api.party.intensity(selection, 0))}>Off</button>
            <input type="range" min="0" max="100" step="1" aria-label="Intensity of the selection"
              disabled={selection.length === 0}
              value={Math.round((selectionIntensity ?? 0) * 100)}
              oninput={(e) => setIntensity(Number(e.currentTarget.value) / 100)} />
            <span class="mono level">{selectionIntensity === null ? (selection.length ? 'mixed' : '–') : `${Math.round(selectionIntensity * 100)}%`}</span>
            <button class="btn" type="button" disabled={selection.length === 0}
              onclick={() => run(api.party.intensity(selection, 1))}>Full</button>
          </div>
        </section>

        <section class="glass panel">
          <h3>Fixtures</h3>
          <div class="fixtures">
            {#each fixtures as fixture (fixture.id)}
              <span class="fixture" class:selected={selection.includes(fixture.id)} style={fixtureGlow(fixture)}
                title={fixture.name}>
                <span class="glow"></span>{fixture.shortName ?? fixture.name}
              </span>
            {/each}
          </div>
        </section>
      </div>

      <aside class="buttons glass">
        <h3>Buttons</h3>
        <div class="grid">
          {#each status?.buttons ?? [] as button, i (button.id)}
            <button type="button" class="party-button" class:on={button.on} aria-pressed={button.on}
              title={button.mode === 'flash' ? 'Flash: on while held' : 'Toggle'}
              onpointerdown={() => press(button)} onpointerup={() => release(button)}
              onpointerleave={() => release(button)} onpointercancel={() => release(button)}>
              <span class="name">{button.name}</span>
              <span class="meta">{button.mode === 'flash' ? 'Flash' : 'Toggle'}{#if buttonKey(i)} · <kbd>{buttonKey(i)}</kbd>{/if}</span>
            </button>
          {:else}
            <p class="dim small">
              No party buttons yet. In setup mode, set gobos, prisms or any channel in the programmer and record them in
              the “Party” tab.
            </p>
          {/each}
        </div>
      </aside>
    </div>
  </section>
{/if}

<style>
  .party {
    display: flex;
    flex-direction: column;
    gap: var(--gap);
    height: 100%;
    min-height: 0;
  }

  .empty {
    padding: 40px;
    text-align: center;
  }

  h3 {
    margin: 0 0 10px;
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .tempo {
    display: flex;
    align-items: center;
    gap: 22px;
    padding: 12px 18px;
  }

  .bpm {
    display: flex;
    align-items: baseline;
    gap: 6px;
  }

  .bpm .value {
    font-size: 34px;
    font-weight: 700;
    color: var(--accent);
    min-width: 2.2em;
    text-align: right;
  }

  .bpm .unit {
    font-size: 12px;
    color: var(--text-dim);
  }

  .beats {
    display: flex;
    gap: 8px;
  }

  .beat {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 1px solid var(--border-strong);
    background: rgba(255, 255, 255, 0.04);
  }

  .beat.on {
    background: var(--accent);
    box-shadow: 0 0 calc(4px + 16px * var(--pulse)) var(--accent-glow);
    opacity: calc(0.45 + 0.55 * var(--pulse));
  }

  .tap {
    min-width: 110px;
    font-weight: 700;
    letter-spacing: 0.1em;
  }

  kbd {
    font-family: var(--mono);
    font-size: 10px;
    padding: 1px 5px;
    border-radius: 4px;
    border: 1px solid var(--border-strong);
    color: var(--text-dim);
  }

  .bpm-form input {
    width: 70px;
    min-height: 30px;
    margin-left: 6px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .bpm-form label {
    font-size: 13px;
    color: var(--text-dim);
  }

  .spacer {
    flex: 1;
  }

  .experimental {
    color: var(--warning);
    border-color: var(--warning);
  }

  .layout {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: 220px 1fr 300px;
    gap: var(--gap);
  }

  .groups,
  .buttons {
    padding: 16px;
    overflow: auto;
  }

  .groups {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .chip {
    display: flex;
    justify-content: space-between;
    align-items: center;
    min-height: 44px;
    padding: 0 14px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    border-left: 4px solid var(--group, var(--border-strong));
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
  }

  .chip.selected {
    border-color: var(--accent);
    background: var(--accent-soft);
    box-shadow: 0 0 14px var(--accent-glow);
  }

  .count {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--text-dim);
  }

  .selection {
    margin-top: auto;
  }

  .center {
    display: flex;
    flex-direction: column;
    gap: var(--gap);
    min-height: 0;
    overflow: auto;
  }

  .panel {
    padding: 16px;
  }

  .palettes {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 10px;
  }

  .palette {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 12px 6px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .palette:disabled {
    opacity: 0.4;
    cursor: default;
  }

  .palette .swatch {
    width: 100%;
    height: 44px;
    border-radius: 8px;
    background: var(--swatch);
    box-shadow: 0 0 18px color-mix(in srgb, var(--swatch) 45%, transparent);
  }

  .palette.current {
    border-color: var(--accent);
    box-shadow: 0 0 0 1px var(--accent);
  }

  .intensity {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .intensity input {
    flex: 1;
    accent-color: var(--accent);
  }

  .level {
    width: 52px;
    text-align: right;
    font-size: 13px;
  }

  .fixtures {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .fixture {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px 4px 6px;
    border-radius: 20px;
    border: 1px solid var(--border);
    font-size: 12px;
  }

  .fixture.selected {
    border-color: var(--accent);
  }

  .glow {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--color);
    opacity: calc(0.12 + 0.88 * var(--intensity));
    box-shadow: 0 0 calc(10px * var(--intensity)) var(--color);
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .party-button {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: 8px;
    min-height: 74px;
    padding: 10px 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: var(--panel-raised);
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: none;
    user-select: none;
  }

  .party-button.on {
    border-color: var(--accent);
    background: var(--accent-soft);
    box-shadow: 0 0 18px var(--accent-glow);
  }

  .party-button .name {
    font-weight: 700;
  }

  .party-button .meta {
    font-size: 11px;
    color: var(--text-dim);
  }

  @media (max-width: 1100px) {
    .layout {
      grid-template-columns: 180px 1fr;
    }

    .buttons {
      grid-column: 1 / -1;
    }
  }
</style>
