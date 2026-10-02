<script lang="ts">
  import { api } from '../lib/api';
  import { modesOf } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { channelOwners, fixtureHue, UNIVERSE_SIZE } from '../lib/patch';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Fixture, Universe } from '../lib/types';

  /** embedded in the show view: without page heading, inside the show's panel */
  let { embedded = false }: { embedded?: boolean } = $props();

  let universes = $state<Universe[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let universeNumber = $state<number | null>(null);
  let percent = $state(false);
  let onlyPatched = $state(false);

  const projectId = $derived(app.active?.id ?? null);
  const universe = $derived(universes.find((u) => u.number === universeNumber) ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const owners = $derived(
    channelOwners(fixtures.filter((f) => f.universeId === universe?.id), modes),
  );
  const values = $derived(universeNumber !== null ? live.dmx[universeNumber] : undefined);
  const active = $derived(values ? values.filter((v) => v > 0).length : 0);

  $effect(() => {
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    if (universeNumber !== null) return live.watch(`dmx:${universeNumber}`);
  });

  async function load(id: number) {
    try {
      [universes, fixtures] = await Promise.all([api.universes.list(id), api.fixtures.list(id)]);
      if (!universes.some((u) => u.number === universeNumber)) {
        universeNumber = universes[0]?.number ?? null;
      }
    } catch (e) {
      showError(e);
    }
  }

  function show(value: number): string {
    return percent ? String(Math.round((value / 255) * 100)) : String(value);
  }

  function describe(channel: number, value: number): string {
    const owner = owners[channel - 1];
    const what = owner ? `${owner.fixture.name} – ${owner.channel}` : 'not patched';
    return `Channel ${channel}: ${value} (${Math.round((value / 255) * 100)} %) · ${what}`;
  }
</script>

<section class="view" class:embedded>
  <header class="head">
    {#if !embedded}
      <div>
        <h1>DMX monitor</h1>
        <p class="dim">The 512 channel values Lumen sends right now, after programmer, playback and grand master.</p>
      </div>
    {/if}
    <div class="controls">
      {#if universes.length > 0}
        <label class="field">Universe
          <select bind:value={universeNumber}>
            {#each universes as u (u.id)}
              <option value={u.number}>{u.displayName}</option>
            {/each}
          </select>
        </label>
      {/if}
      <label class="toggle"><input type="checkbox" bind:checked={percent} /> Percent</label>
      <label class="toggle"><input type="checkbox" bind:checked={onlyPatched} /> Patched only</label>
    </div>
  </header>

  {#if !app.active}
    <div class="glass empty">Activate a project to see its output.</div>
  {:else if universes.length === 0}
    <div class="glass empty">This project has no universes yet.</div>
  {:else}
    <div class="panel" class:glass={!embedded}>
      <p class="small dim">
        {#if values}{active} of {UNIVERSE_SIZE} channels above 0{:else}Waiting for data…{/if}
      </p>
      <div class="grid" role="grid" aria-label="DMX values of universe {universeNumber}">
        {#each { length: UNIVERSE_SIZE } as _, index (index)}
          {@const channel = index + 1}
          {@const value = values?.[index] ?? 0}
          {@const owner = owners[index]}
          {#if !onlyPatched || owner}
            <div class="cell" class:patched={owner} class:start={owner && owner.fixture.address === channel}
              style="--level: {value / 255}; {owner ? `--hue: ${fixtureHue(owner.fixture.id)}` : ''}"
              title={describe(channel, value)} role="gridcell">
              <span class="channel mono">{channel}</span>
              <span class="value mono">{show(value)}</span>
            </div>
          {/if}
        {/each}
      </div>
    </div>
  {/if}
</section>

<style>
  .view {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 12px 8px;
  }

  .head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .head p {
    margin: 6px 0 0;
  }

  .controls {
    display: flex;
    align-items: flex-end;
    gap: 16px;
  }

  .toggle {
    display: flex;
    align-items: center;
    gap: 6px;
    min-height: 38px;
    font-size: 13px;
  }

  .toggle input {
    accent-color: var(--accent);
  }

  .panel {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 14px;
  }

  .small {
    font-size: 12px;
    margin: 0;
  }

  .panel {
    container-type: inline-size;
  }

  /* as many channels per row as fit: 32 on a wide screen, fewer in the show view or on a tablet */
  .grid {
    display: grid;
    grid-template-columns: repeat(8, minmax(0, 1fr));
    gap: 2px;
  }

  @container (min-width: 440px) {
    .grid {
      grid-template-columns: repeat(16, minmax(0, 1fr));
    }
  }

  @container (min-width: 900px) {
    .grid {
      grid-template-columns: repeat(32, minmax(0, 1fr));
    }
  }

  .view.embedded {
    padding: 0;
    gap: 10px;
  }

  .view.embedded .head {
    justify-content: flex-start;
  }

  .view.embedded .panel {
    padding: 0;
  }

  .cell {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 34px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--accent) calc(var(--level) * 75%), rgba(255, 255, 255, 0.03));
    border-bottom: 2px solid transparent;
    overflow: hidden;
  }

  .cell.patched {
    border-bottom-color: hsl(var(--hue) 70% 55% / 0.8);
  }

  .cell.start {
    box-shadow: inset 2px 0 0 hsl(var(--hue) 70% 55%);
  }

  .channel {
    font-size: 8px;
    color: var(--text-faint);
  }

  .value {
    font-size: 11px;
    font-weight: 600;
  }

</style>
