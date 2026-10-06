<script lang="ts">
  import PartyGrid from '../components/PartyGrid.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import {
    cellAt, cellLabel, emptyCell, groupNames, MAX_MATRIX_SIZE, resized, toggledSeparator, withCell, withoutCell,
  } from '../lib/party';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Composition, KeyMode, PartyCell, PartyMatrix, Scene } from '../lib/types';

  type Kind = 'empty' | 'text' | 'scene' | 'composition';

  let matrix = $state<PartyMatrix>({ rows: 4, columns: 4, separators: [], cells: [] });
  let scenes = $state<Scene[]>([]);
  let compositions = $state<Composition[]>([]);
  let selected = $state<{ row: number; column: number } | null>(null);

  const projectId = $derived(app.active?.id ?? null);
  const scenesById = $derived(new Map(scenes.map((s) => [s.id, s])));
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const cell = $derived(selected ? cellAt(matrix, selected.row, selected.column) : null);
  const kind = $derived<Kind>(!cell ? 'empty' : cell.sceneId !== null ? 'scene'
    : cell.compositionId !== null ? 'composition' : 'text');
  const groups = $derived(groupNames(matrix));
  const sortedCompositions = $derived([...compositions].sort((a, b) => a.name.localeCompare(b.name)));

  $effect(() => {
    void live.partyMatrixRevision;
    void live.scenesRevision;
    void live.compositionsRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [matrix, scenes, compositions] = await Promise.all([api.partyMatrix.get(id), api.scenes.list(id),
        api.compositions.list(id)]);
    } catch (e) {
      showError(e);
    }
  }

  /** Every change is saved at once; the server answers with the stored matrix. */
  async function save(next: PartyMatrix) {
    if (projectId === null) return;
    const before = matrix;
    matrix = next;
    try {
      matrix = await api.partyMatrix.save(projectId, next);
    } catch (e) {
      matrix = before;
      showError(e);
    }
  }

  function update(changes: Partial<PartyCell>) {
    if (!selected) return;
    const current = cell ?? emptyCell(selected.row, selected.column);
    save(withCell(matrix, { ...current, ...changes }));
  }

  function setKind(next: Kind) {
    if (!selected) return;
    if (next === 'empty') {
      save(withoutCell(matrix, selected.row, selected.column));
    } else if (next === 'text') {
      update({ sceneId: null, compositionId: null, label: cell?.label ?? 'Text' });
    } else if (next === 'scene') {
      update({ sceneId: scenes[0]?.id ?? null, compositionId: null });
    } else {
      update({ compositionId: sortedCompositions[0]?.id ?? null, sceneId: null });
    }
  }

  function resize(rows: number, columns: number) {
    const next = resized(matrix, rows, columns);
    if (selected && (selected.row >= next.rows || selected.column >= next.columns)) selected = null;
    save(next);
  }
</script>

{#if !app.active}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else}
  <section class="view">
    <header class="head">
      <div>
        <h1>Party</h1>
        <p class="dim">
          A matrix of fields for party mode. Each field switches a scene or a composition on and off – or just shows a
          text. Any number can be on at once; where they collide, the higher value wins. Fields of the same group
          exclude each other (e.g. colors: blue goes off when red goes on). Click between two rows for a line.
        </p>
      </div>
      <div class="size glass">
        <label>Rows <input type="number" min="1" max={MAX_MATRIX_SIZE} value={matrix.rows}
          onchange={(e) => resize(Number(e.currentTarget.value), matrix.columns)} /></label>
        <label>Columns <input type="number" min="1" max={MAX_MATRIX_SIZE} value={matrix.columns}
          onchange={(e) => resize(matrix.rows, Number(e.currentTarget.value))} /></label>
      </div>
    </header>

    <div class="layout">
      <div class="grid glass">
        <PartyGrid {matrix} scenes={scenesById} compositions={compositionsById} editing {selected}
          onselect={(row, column) => (selected = { row, column })}
          ontoggleseparator={(row) => save(toggledSeparator(matrix, row))} />
      </div>

      <aside class="editor glass">
        {#if !selected}
          <p class="dim">Select a field to set it up.</p>
        {:else}
          <h3>Field {selected.row + 1} / {selected.column + 1}</h3>
          <div class="kinds" role="radiogroup" aria-label="What the field does">
            {#each [['empty', 'Empty'], ['text', 'Text'], ['scene', 'Scene'], ['composition', 'Composition']] as [value, text] (value)}
              <button type="button" class="btn small" class:active={kind === value} role="radio"
                aria-checked={kind === value} onclick={() => setKind(value as Kind)}
                disabled={(value === 'scene' && scenes.length === 0)
                  || (value === 'composition' && compositions.length === 0)}>{text}</button>
            {/each}
          </div>

          {#if cell}
            {#if kind === 'scene'}
              <label class="field">Scene
                <select value={cell.sceneId} onchange={(e) => update({ sceneId: Number(e.currentTarget.value) })}>
                  {#if cell.sceneId === null || !scenesById.has(cell.sceneId)}<option value={cell.sceneId}>(missing scene)</option>{/if}
                  {#each scenes as scene (scene.id)}<option value={scene.id}>{scene.number} {scene.name}</option>{/each}
                </select>
              </label>
              <p class="hint dim">Plays all compositions of the scene, without its fades and keys.</p>
            {:else if kind === 'composition'}
              <label class="field">Composition
                <select value={cell.compositionId}
                  onchange={(e) => update({ compositionId: Number(e.currentTarget.value) })}>
                  {#if cell.compositionId === null || !compositionsById.has(cell.compositionId)}<option value={cell.compositionId}>(missing composition)</option>{/if}
                  {#each sortedCompositions as composition (composition.id)}
                    <option value={composition.id}>{composition.name}</option>
                  {/each}
                </select>
              </label>
            {/if}

            <label class="field">Label
              <input value={cell.label ?? ''} maxlength="60"
                placeholder={kind === 'text' ? 'Text' : cellLabel({ ...cell, label: null }, scenesById, compositionsById)}
                onchange={(e) => update({ label: e.currentTarget.value.trim() || null })} />
            </label>

            <div class="colors">
              <label class="field">Color<input type="color" value={cell.color}
                onchange={(e) => update({ color: e.currentTarget.value })} /></label>
              <label class="field">Border<input type="color" value={cell.borderColor}
                onchange={(e) => update({ borderColor: e.currentTarget.value })} /></label>
            </div>

            {#if kind !== 'text'}
              <label class="field">Mode
                <select value={cell.mode} onchange={(e) => update({ mode: e.currentTarget.value as KeyMode })}>
                  <option value="toggle">Toggle – press on, press off</option>
                  <option value="flash">Flash – on while held</option>
                </select>
              </label>
              <label class="field">Group
                <input list="party-groups" value={cell.group ?? ''} placeholder="None – runs alongside everything"
                  onchange={(e) => update({ group: e.currentTarget.value.trim() || null })} />
              </label>
              <datalist id="party-groups">{#each groups as group (group)}<option value={group}></option>{/each}</datalist>
              <p class="hint dim">Only one field of a group is on at a time.</p>
            {/if}
          {/if}
        {/if}
      </aside>
    </div>
  </section>
{/if}

<style>
  .view {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 12px 8px;
  }

  .empty {
    padding: 40px;
    text-align: center;
  }

  .head {
    display: flex;
    align-items: flex-end;
    gap: 16px;
    flex-wrap: wrap;
  }

  .head > div:first-child {
    flex: 1;
    min-width: 260px;
  }

  .head p {
    margin: 6px 0 0;
    max-width: 820px;
  }

  .size {
    display: flex;
    gap: 14px;
    padding: 8px 12px;
    box-shadow: none;
    font-size: 12px;
    color: var(--text-dim);
  }

  .size input {
    width: 60px;
    min-height: 30px;
    margin-left: 4px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    gap: 12px;
    align-items: start;
  }

  .grid {
    padding: 16px;
    min-width: 0;
  }

  .editor {
    position: sticky;
    top: 84px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .editor h3 {
    margin: 0;
  }

  .kinds {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .kinds .active {
    border-color: var(--accent);
    color: var(--accent);
  }

  .colors {
    display: flex;
    gap: 12px;
  }

  .colors .field {
    flex: 1;
  }

  .colors input {
    padding: 4px;
  }

  .hint {
    margin: -6px 0 0;
    font-size: 11px;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .editor {
      position: static;
    }
  }
</style>
