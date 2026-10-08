<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from '../lib/api';
  import { cueRows, sheetSeconds, type CueRow } from '../lib/cueSheet';
  import { curveLabel } from '../lib/sceneTiming';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Composition, Move, Scene } from '../lib/types';

  let scenes = $state<Scene[]>([]);
  let compositions = $state<Composition[]>([]);
  let moves = $state<Move[]>([]);
  let loaded = $state(false);

  const project = $derived(app.active);
  const projectId = $derived(app.active?.id ?? null);
  const rows = $derived<CueRow[]>(project ? cueRows(project, scenes, compositions, moves) : []);
  const printedAt = new Date().toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });

  $effect(() => {
    if (projectId !== null) load(projectId);
  });

  // paper colors for the whole page while the sheet is open
  onMount(() => {
    document.body.classList.add('paper');
    return () => document.body.classList.remove('paper');
  });

  async function load(id: number) {
    try {
      [scenes, compositions, moves] = await Promise.all([api.scenes.list(id), api.compositions.list(id),
        api.moves.list(id)]);
      loaded = true;
    } catch (e) {
      showError(e);
    }
  }

  function back() {
    if (history.length > 1) history.back();
    else location.hash = '/setup/scenes';
  }
</script>

<div class="sheet">
  <div class="toolbar no-print">
    <button class="btn" type="button" onclick={back}>← Back</button>
    <span class="dim">Use “Save as PDF” in the print dialog for a PDF.</span>
    <button class="btn primary" type="button" onclick={() => window.print()} disabled={!loaded}>🖨 Print</button>
  </div>

  {#if !project}
    <p>No active project.</p>
  {:else}
    <header>
      <div>
        <h1>{project.name} – Cue list</h1>
        {#if project.venue}<div class="sub">{project.venue}</div>{/if}
      </div>
      <div class="meta">
        <div>Printed {printedAt}</div>
        <div>Project fades: in {sheetSeconds(project.fadeInMs)} · out {sheetSeconds(project.fadeOutMs)} · {curveLabel(project.fadeCurve)}</div>
        <div>{rows.length} {rows.length === 1 ? 'cue' : 'cues'}</div>
      </div>
    </header>

    {#if loaded && rows.length === 0}
      <p>No scenes in the cue list yet.</p>
    {:else if loaded}
      <table>
        <thead>
          <tr>
            <th class="num">#</th>
            <th>Scene</th>
            <th>GO on</th>
            <th class="time">In</th>
            <th class="time">Out</th>
            <th class="time">Delay</th>
            <th class="time">Follow</th>
            <th>Running / keys</th>
            <th class="check" aria-label="Done"></th>
          </tr>
        </thead>
        <tbody>
          {#each rows as row (row.sceneId)}
            <tr class:follows={row.autoFollowMs !== null}>
              <td class="num">{row.number}</td>
              <td class="name">{row.name}</td>
              <td class="note">{row.cueNote ?? ''}</td>
              <td class="time" class:own={row.fadeIn.own}>{sheetSeconds(row.fadeIn.ms)}</td>
              <td class="time" class:own={row.fadeOut.own}>{sheetSeconds(row.fadeOut.ms)}</td>
              <td class="time" class:own={row.delayMs > 0}>{row.delayMs > 0 ? sheetSeconds(row.delayMs) : ''}</td>
              <td class="time">{row.autoFollowMs !== null ? `→ ${sheetSeconds(row.autoFollowMs)}` : ''}</td>
              <td class="extra">
                {#if row.compositions.length > 0}<div>{row.compositions.join(', ')}</div>{/if}
                {#if row.moves.length > 0}<div class="keys">Moves: {row.moves.join(', ')}</div>{/if}
                {#if row.keys.length > 0}<div class="keys">Keys: {row.keys.join(', ')}</div>{/if}
              </td>
              <td class="check"><span class="box"></span></td>
            </tr>
          {/each}
        </tbody>
      </table>

      <p class="legend">
        <strong>Bold</strong> times are the scene's own, the others the project's. “Follow”: the next cue starts by itself
        this long after the fade-in. The fade into a scene uses the times of that scene.
      </p>

    {/if}
  {/if}
</div>

<style>
  :global(body.paper) {
    background: var(--paper);
    color: var(--paper-ink);
    color-scheme: light;
  }

  .sheet {
    max-width: 1100px;
    margin: 0 auto;
    padding: 24px 28px 48px;
    color: var(--paper-ink);
    font-size: 13px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
  }

  .toolbar .btn:not(.primary) {
    color: var(--paper-ink);
    background: transparent;
    border-color: var(--paper-rule);
  }

  .toolbar .dim {
    flex: 1;
    color: var(--paper-dim);
  }

  header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    gap: 24px;
    padding-bottom: 10px;
    border-bottom: 2px solid var(--paper-ink);
  }

  h1 {
    margin: 0;
    font-size: 22px;
  }

  .sub,
  .meta {
    color: var(--paper-dim);
  }

  .meta {
    text-align: right;
    font-size: 11px;
    line-height: 1.5;
  }

  table {
    width: 100%;
    margin-top: 10px;
    border-collapse: collapse;
  }

  th {
    text-align: left;
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--paper-dim);
    padding: 6px 6px;
    border-bottom: 1px solid var(--paper-ink);
  }

  td {
    padding: 7px 6px;
    vertical-align: top;
    border-bottom: 1px solid var(--paper-rule);
  }

  tr {
    break-inside: avoid;
  }

  tr.follows td {
    border-bottom-style: dashed;
  }

  .num {
    width: 48px;
    font-family: var(--mono);
    font-weight: 700;
    font-size: 15px;
  }

  .name {
    font-weight: 600;
    min-width: 120px;
  }

  .note {
    font-style: italic;
  }

  .time {
    width: 54px;
    white-space: nowrap;
    font-family: var(--mono);
    font-size: 12px;
    color: var(--paper-dim);
  }

  .time.own {
    font-weight: 700;
    color: var(--paper-ink);
  }

  .extra {
    font-size: 12px;
  }

  .keys {
    color: var(--paper-dim);
  }

  .check {
    width: 22px;
  }

  .box {
    display: block;
    width: 12px;
    height: 12px;
    border: 1px solid var(--paper-ink);
    border-radius: 2px;
  }

  .legend {
    margin-top: 10px;
    font-size: 11px;
    color: var(--paper-dim);
  }

  @media print {
    @page {
      size: A4;
      margin: 12mm;
    }

    .no-print {
      display: none;
    }

    .sheet {
      max-width: none;
      padding: 0;
    }

    /* the header row repeats on every page */
    thead {
      display: table-header-group;
    }
  }
</style>
