<script lang="ts" module>
  const PAGE_KEY = 'lumen.partyPage';

  /** The page last shown on this device. */
  function rememberedPage(): number {
    try {
      return Math.max(0, Number(localStorage.getItem(PAGE_KEY)) || 0);
    } catch {
      return 0;
    }
  }
</script>

<script lang="ts">
  import { onMount } from 'svelte';
  import PartyGrid from '../components/PartyGrid.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import { wakeLock } from '../lib/wakeLock.svelte';
  import type { Composition, Move, PartyCell, PartyMatrix, PartyPlace, PartyStatus, Scene } from '../lib/types';

  let matrix = $state<PartyMatrix | null>(null);
  let scenes = $state<Scene[]>([]);
  let compositions = $state<Composition[]>([]);
  let moves = $state<Move[]>([]);
  let chosenPage = $state(rememberedPage());
  /** flash fields held down, to release them exactly once */
  const held = new Map<string, PartyPlace>();

  const projectId = $derived(app.active?.id ?? null);
  const scenesById = $derived(new Map(scenes.map((s) => [s.id, s])));
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const movesById = $derived(new Map(moves.map((m) => [m.id, m])));
  const onCount = $derived(live.party?.on.length ?? 0);
  const pageIndex = $derived(matrix ? Math.min(chosenPage, matrix.pages.length - 1) : 0);
  const page = $derived(matrix?.pages[pageIndex] ?? null);
  /** fields that are on per page, shown on the tabs */
  const onPerPage = $derived(matrix?.pages.map((_, i) => live.party?.on.filter((p) => p.page === i).length ?? 0)
    ?? []);

  $effect(() => {
    void live.partyMatrixRevision;
    void live.scenesRevision;
    void live.compositionsRevision;
    void live.movesRevision;
    if (projectId !== null) load(projectId);
  });

  onMount(() => {
    api.party.status().then((s) => live.updateParty(s)).catch(showError);
    const releaseWakeLock = wakeLock.hold();
    return () => {
      releaseWakeLock();
      held.forEach((place) => api.party.release(place).catch(() => {}));
    };
  });

  async function load(id: number) {
    try {
      [matrix, scenes, compositions, moves] = await Promise.all([api.partyMatrix.get(id), api.scenes.list(id),
        api.compositions.list(id), api.moves.list(id)]);
    } catch (e) {
      showError(e);
    }
  }

  function showPage(index: number) {
    chosenPage = index;
    try {
      localStorage.setItem(PAGE_KEY, String(index));
    } catch {
      // only a convenience
    }
  }

  async function run(request: Promise<PartyStatus>) {
    try {
      live.updateParty(await request);
    } catch (e) {
      showError(e);
    }
  }

  const placeOf = (cell: PartyCell): PartyPlace => ({ page: pageIndex, row: cell.row, column: cell.column });
  const keyOf = (place: PartyPlace) => `${place.page}/${place.row}/${place.column}`;

  function press(cell: PartyCell) {
    const place = placeOf(cell);
    if (held.has(keyOf(place))) return;
    if (cell.mode === 'flash') held.set(keyOf(place), place);
    run(api.party.press(place));
  }

  function release(cell: PartyCell) {
    const place = placeOf(cell);
    if (!held.delete(keyOf(place))) return;
    run(api.party.release(place));
  }
</script>

{#if !app.active}
  <div class="glass empty"><h2>No active project</h2></div>
{:else}
  <section class="party">
    <header class="bar glass">
      <h2>Party</h2>
      {#if matrix && matrix.pages.length > 1}
        <div class="pages" role="tablist" aria-label="Pages">
          {#each matrix.pages as p, i (i)}
            <button type="button" role="tab" aria-selected={i === pageIndex} class:active={i === pageIndex}
              onclick={() => showPage(i)}>
              {p.name}{#if onPerPage[i] > 0}<span class="count" title="{onPerPage[i]} on">{onPerPage[i]}</span>{/if}
            </button>
          {/each}
        </div>
      {/if}
      <span class="spacer"></span>
      <span class="dim small">{onCount === 0 ? 'Everything off' : `${onCount} on`}</span>
      <button class="btn" type="button" onclick={() => run(api.party.clear())} disabled={onCount === 0}
        title="Switch every field off, on all pages">All off</button>
    </header>

    {#if page && page.cells.length > 0}
      <div class="grid glass">
        <PartyGrid {page} {pageIndex} fill scenes={scenesById} compositions={compositionsById} moves={movesById}
          status={live.party} onpress={press} onrelease={release}
          onslide={(cell, x, y) => live.slideParty(placeOf(cell), x, y)} />
      </div>
    {:else if matrix}
      <div class="glass empty">
        <h2>{matrix.pages.length > 1 ? `The page "${page?.name}" is empty` : 'The party matrix is empty'}</h2>
        <p class="dim">Set up its fields in setup mode under Party.</p>
      </div>
    {/if}
  </section>
{/if}

<style>
  /* the whole matrix in the window: the grid gets the room the bar leaves, no scrolling */
  .party {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 0 8px;
  }

  .bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding: 10px 16px;
  }

  .bar h2 {
    margin: 0;
    font-size: 18px;
  }

  .pages {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .pages button {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 40px;
    padding: 0 16px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
    touch-action: manipulation;
  }

  .pages button.active {
    border-color: var(--accent);
    color: var(--accent);
    background: var(--accent-soft);
  }

  .count {
    min-width: 20px;
    padding: 0 6px;
    border-radius: 999px;
    background: var(--accent);
    color: var(--accent-ink);
    font-size: 11px;
    text-align: center;
  }

  .spacer {
    flex: 1;
  }

  .small {
    font-size: 12px;
  }

  .grid {
    flex: 1;
    min-height: 0;
    padding: 12px;
  }

  .empty {
    padding: 40px;
    text-align: center;
  }
</style>
