<script lang="ts">
  import { onMount } from 'svelte';
  import PartyGrid from '../components/PartyGrid.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import type { Composition, PartyCell, PartyMatrix, PartyStatus, Scene } from '../lib/types';

  let matrix = $state<PartyMatrix | null>(null);
  let scenes = $state<Scene[]>([]);
  let compositions = $state<Composition[]>([]);
  /** flash fields held down, to release them exactly once */
  const held = new Set<string>();

  const projectId = $derived(app.active?.id ?? null);
  const scenesById = $derived(new Map(scenes.map((s) => [s.id, s])));
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const onCount = $derived(live.party?.on.length ?? 0);

  $effect(() => {
    void live.partyMatrixRevision;
    void live.scenesRevision;
    void live.compositionsRevision;
    if (projectId !== null) load(projectId);
  });

  onMount(() => {
    api.party.status().then((s) => live.updateParty(s)).catch(showError);
    const releaseWakeLock = wakeLock.hold();
    return () => {
      releaseWakeLock();
      held.forEach((key) => {
        const [row, column] = key.split('/').map(Number);
        api.party.release(row, column).catch(() => {});
      });
    };
  });

  async function load(id: number) {
    try {
      [matrix, scenes, compositions] = await Promise.all([api.partyMatrix.get(id), api.scenes.list(id),
        api.compositions.list(id)]);
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

  function press(cell: PartyCell) {
    const key = `${cell.row}/${cell.column}`;
    if (held.has(key)) return;
    if (cell.mode === 'flash') held.add(key);
    run(api.party.press(cell.row, cell.column));
  }

  function release(cell: PartyCell) {
    if (!held.delete(`${cell.row}/${cell.column}`)) return;
    run(api.party.release(cell.row, cell.column));
  }
</script>

{#if !app.active}
  <div class="glass empty"><h2>No active project</h2></div>
{:else}
  <section class="party">
    <header class="bar glass">
      <h2>Party</h2>
      <span class="dim small">{onCount === 0 ? 'Everything off' : `${onCount} on`}</span>
      <span class="spacer"></span>
      <button class="btn" type="button" onclick={() => run(api.party.clear())} disabled={onCount === 0}
        title="Switch every field off">All off</button>
    </header>

    {#if matrix && matrix.cells.length > 0}
      <div class="grid glass">
        <PartyGrid {matrix} scenes={scenesById} compositions={compositionsById} status={live.party}
          onpress={press} onrelease={release} />
      </div>
    {:else}
      <div class="glass empty">
        <h2>The party matrix is empty</h2>
        <p class="dim">Set up its fields in setup mode under Party.</p>
      </div>
    {/if}
  </section>
{/if}

<style>
  .party {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 12px 8px;
  }

  .bar {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 16px;
  }

  .bar h2 {
    margin: 0;
    font-size: 18px;
  }

  .spacer {
    flex: 1;
  }

  .small {
    font-size: 12px;
  }

  .grid {
    padding: 16px;
  }

  .empty {
    padding: 40px;
    text-align: center;
  }
</style>
