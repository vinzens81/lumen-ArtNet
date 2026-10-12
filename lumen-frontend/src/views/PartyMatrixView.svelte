<script lang="ts">
  import PartyGrid from '../components/PartyGrid.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { canMove } from '../lib/moves';
  import {
    cellAt, cellLabel, emptyCell, emptyPage, groupNames, MAX_MATRIX_SIZE, MAX_PAGE_NAME, MAX_PAGES, maxRowSpan,
    maxSpan, movedPage, newPageName, resized, toggledSeparator, withCell, withoutCell, withPage,
  } from '../lib/party';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type {
    Composition, Fixture, KeyMode, Move, PartyCell, PartyFader, PartyMatrix, PartyPage, Scene,
  } from '../lib/types';

  type Kind = 'empty' | 'text' | 'scene' | 'composition' | 'move' | 'gradient' | 'pad';

  let matrix = $state<PartyMatrix>({ pages: [emptyPage('Party')] });
  let scenes = $state<Scene[]>([]);
  let compositions = $state<Composition[]>([]);
  let moves = $state<Move[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let pageIndex = $state(0);
  let selected = $state<{ row: number; column: number } | null>(null);
  let confirmRemovePage = $state(false);

  const projectId = $derived(app.active?.id ?? null);
  const scenesById = $derived(new Map(scenes.map((s) => [s.id, s])));
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const movesById = $derived(new Map(moves.map((m) => [m.id, m])));
  const current = $derived(Math.min(pageIndex, matrix.pages.length - 1));
  const page = $derived(matrix.pages[current]);
  const cell = $derived(selected ? cellAt(page, selected.row, selected.column) : null);
  const kind = $derived<Kind>(!cell ? 'empty' : cell.sceneId !== null ? 'scene'
    : cell.compositionId !== null ? 'composition' : cell.moveId !== null ? 'move'
      : cell.fader?.kind === 'pan-tilt' ? 'pad' : cell.fader !== null ? 'gradient' : 'text');
  const widest = $derived(selected ? maxSpan(page, selected.row, selected.column, cell?.rowSpan ?? 1) : 1);
  const tallest = $derived(selected ? maxRowSpan(page, selected.row, selected.column, cell?.span ?? 1) : 1);
  const groups = $derived(groupNames(matrix));
  const sortedCompositions = $derived([...compositions].sort((a, b) => a.name.localeCompare(b.name)));
  /** fixtures with pan and tilt: what a pad can move */
  const movingFixtures = $derived(fixtures.filter((f) =>
    canMove(app.fixtureType(f.fixtureTypeId)?.modes.find((m) => m.name === f.mode))));
  const faderFixtures = $derived(kind === 'pad' ? movingFixtures : fixtures);

  $effect(() => {
    void live.partyMatrixRevision;
    void live.scenesRevision;
    void live.compositionsRevision;
    void live.movesRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [matrix, scenes, compositions, moves, fixtures] = await Promise.all([api.partyMatrix.get(id),
        api.scenes.list(id), api.compositions.list(id), api.moves.list(id), api.fixtures.list(id)]);
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

  function savePage(next: PartyPage) {
    save(withPage(matrix, current, next));
  }

  function showPage(index: number) {
    pageIndex = index;
    selected = null;
    confirmRemovePage = false;
  }

  function addPage() {
    save({ pages: [...matrix.pages, emptyPage(newPageName(matrix))] });
    showPage(matrix.pages.length);
  }

  function removePage() {
    if (!confirmRemovePage) {
      confirmRemovePage = true;
      return;
    }
    save({ pages: matrix.pages.filter((_, i) => i !== current) });
    showPage(Math.max(0, current - 1));
  }

  function movePage(by: -1 | 1) {
    save(movedPage(matrix, current, by));
    pageIndex = current + by;
  }

  function rename(name: string) {
    const trimmed = name.trim();
    if (trimmed && trimmed !== page.name) savePage({ ...page, name: trimmed });
  }

  function update(changes: Partial<PartyCell>) {
    if (!selected) return;
    const base = cell ?? emptyCell(selected.row, selected.column);
    savePage(withCell(page, { ...base, ...changes }));
  }

  function setKind(next: Kind) {
    if (!selected) return;
    const none = { sceneId: null, compositionId: null, moveId: null, fader: null };
    if (next === 'empty') {
      savePage(withoutCell(page, selected.row, selected.column));
    } else if (next === 'text') {
      update({ ...none, label: cell?.label ?? 'Text' });
    } else if (next === 'scene') {
      update({ ...none, sceneId: scenes[0]?.id ?? null });
    } else if (next === 'move') {
      update({ ...none, moveId: moves[0]?.id ?? null });
    } else if (next === 'gradient') {
      update({ ...none, fader: { kind: 'dimmer', fixtureIds: fixtures.map((f) => f.id) }, mode: 'toggle',
        span: Math.max(cell?.span ?? 1, Math.min(3, widest)) });
    } else if (next === 'pad') {
      // two by two where there is room
      const span = Math.max(cell?.span ?? 1, Math.min(2, maxSpan(page, selected.row, selected.column)));
      const rowSpan = Math.max(cell?.rowSpan ?? 1, Math.min(2, maxRowSpan(page, selected.row, selected.column, span)));
      update({ ...none, fader: { kind: 'pan-tilt', fixtureIds: movingFixtures.map((f) => f.id) }, mode: 'toggle',
        span, rowSpan });
    } else {
      update({ ...none, compositionId: sortedCompositions[0]?.id ?? null });
    }
  }

  /** Empties the field – every place it covers, if it reaches over several. */
  function reset() {
    if (!selected) return;
    savePage(withoutCell(page, selected.row, selected.column));
  }

  function updateFader(changes: Partial<PartyFader>) {
    if (!cell?.fader) return;
    const fader = { ...cell.fader, ...changes };
    if (fader.fixtureIds.length > 0) update({ fader });
  }

  function toggleFixture(fixtureId: number) {
    if (!cell?.fader) return;
    const ids = cell.fader.fixtureIds;
    updateFader({ fixtureIds: ids.includes(fixtureId) ? ids.filter((id) => id !== fixtureId) : [...ids, fixtureId] });
  }

  function resize(rows: number, columns: number) {
    const next = resized(page, rows, columns);
    if (selected && (selected.row >= next.rows || selected.column >= next.columns)) selected = null;
    savePage(next);
  }
</script>

{#if !app.active}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else}
  <section class="view">
    <div class="pagebar glass">
      <div class="pages" role="tablist" aria-label="Pages">
        {#each matrix.pages as p, i (i)}
          <button type="button" role="tab" aria-selected={i === current} class:active={i === current}
            onclick={() => showPage(i)}>{p.name}</button>
        {/each}
        <button type="button" class="add" onclick={addPage} disabled={matrix.pages.length >= MAX_PAGES}
          title={matrix.pages.length >= MAX_PAGES ? `At most ${MAX_PAGES} pages` : 'Add a page'}>+ Page</button>
      </div>
      <div class="page-settings">
        {#key `${current}/${page.name}`}
          <label>Name <input class="name" value={page.name} maxlength={MAX_PAGE_NAME} required
            onchange={(e) => rename(e.currentTarget.value)} /></label>
        {/key}
        <label>Rows <input type="number" min="1" max={MAX_MATRIX_SIZE} value={page.rows}
          onchange={(e) => resize(Number(e.currentTarget.value), page.columns)} /></label>
        <label>Columns <input type="number" min="1" max={MAX_MATRIX_SIZE} value={page.columns}
          onchange={(e) => resize(page.rows, Number(e.currentTarget.value))} /></label>
        {#if matrix.pages.length > 1}
          <button type="button" class="btn small" onclick={() => movePage(-1)} disabled={current === 0}
            title="Move the page to the left" aria-label="Move the page to the left">◀</button>
          <button type="button" class="btn small" onclick={() => movePage(1)}
            disabled={current === matrix.pages.length - 1} title="Move the page to the right"
            aria-label="Move the page to the right">▶</button>
          <button type="button" class="btn small danger" onclick={removePage}>
            {confirmRemovePage ? 'Really remove the page?' : 'Remove page'}</button>
        {/if}
      </div>
    </div>

    <div class="layout">
      <div class="grid glass">
        <PartyGrid {page} pageIndex={current} scenes={scenesById} compositions={compositionsById} moves={movesById}
          editing {selected} onselect={(row, column) => (selected = { row, column })}
          ontoggleseparator={(row) => savePage(toggledSeparator(page, row))} />
      </div>

      <aside class="editor glass">
        {#if !selected}
          <p class="dim">Select a field to set it up.</p>
        {:else}
          <div class="editor-head">
            <h3>Field {selected.row + 1} / {selected.column + 1}</h3>
            {#if cell}
              <button type="button" class="btn small" onclick={reset}
                title={cell.span > 1 || cell.rowSpan > 1
                  ? `Empty all ${cell.span * cell.rowSpan} places the field covers` : 'Empty the field'}>
                Reset field</button>
            {/if}
          </div>
          <div class="kinds" role="radiogroup" aria-label="What the field does">
            {#each [['empty', 'Empty'], ['text', 'Text'], ['scene', 'Scene'], ['composition', 'Composition'],
              ['move', 'Move'], ['gradient', 'Gradient'], ['pad', 'Pan/tilt pad']] as [value, text] (value)}
              <button type="button" class="btn small" class:active={kind === value} role="radio"
                aria-checked={kind === value} onclick={() => setKind(value as Kind)}
                disabled={(value === 'scene' && scenes.length === 0)
                  || (value === 'composition' && compositions.length === 0)
                  || (value === 'move' && moves.length === 0)
                  || (value === 'gradient' && fixtures.length === 0)
                  || (value === 'pad' && movingFixtures.length === 0)}
                title={value === 'pad' && movingFixtures.length === 0 ? 'Needs fixtures with pan and tilt'
                  : undefined}>{text}</button>
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
            {:else if kind === 'move'}
              <label class="field">Move
                <select value={cell.moveId} onchange={(e) => update({ moveId: Number(e.currentTarget.value) })}>
                  {#if cell.moveId === null || !movesById.has(cell.moveId)}<option value={cell.moveId}>(missing move)</option>{/if}
                  {#each moves as move (move.id)}<option value={move.id}>{move.name}</option>{/each}
                </select>
              </label>
              <p class="hint dim">Where the heads point while the field is on.</p>
            {:else if (kind === 'gradient' || kind === 'pad') && cell.fader}
              {#if kind === 'gradient'}
              <label class="field">Sets
                <select value={cell.fader.kind}
                  onchange={(e) => updateFader({ kind: e.currentTarget.value as PartyFader['kind'] })}>
                  <option value="dimmer">Intensity – dark to full</option>
                  <option value="color">Color – red to violet</option>
                </select>
              </label>
              {/if}
              <fieldset class="fixtures">
                <legend>Fixtures</legend>
                {#each faderFixtures as fixture (fixture.id)}
                  <label class="check"><input type="checkbox" checked={cell.fader.fixtureIds.includes(fixture.id)}
                    disabled={cell.fader.fixtureIds.length === 1 && cell.fader.fixtureIds.includes(fixture.id)}
                    onchange={() => toggleFixture(fixture.id)} /> {fixture.name}</label>
                {/each}
              </fieldset>
              <p class="hint dim">
                {#if kind === 'pad'}
                  Touch or drag on the field in party mode: left to right is pan, bottom to top is tilt – over the
                  whole range, within the limits set in the patch. Put it in a group with the moves so only one of
                  them points the heads.
                {:else}
                  Touch or drag along the field in party mode. Colors need red, green and blue (or cyan, magenta and
                  yellow); other fixtures are left alone.
                {/if}
              </p>
            {/if}

            <div class="sizes">
              <label class="field">Width
                <select value={cell.span} onchange={(e) => update({ span: Number(e.currentTarget.value) })}>
                  {#each Array.from({ length: Math.max(widest, cell.span) }, (_, i) => i + 1) as span (span)}
                    <option value={span}>{span === 1 ? '1 column' : `${span} columns`}</option>
                  {/each}
                </select>
              </label>
              <label class="field">Height
                <select value={cell.rowSpan} onchange={(e) => update({ rowSpan: Number(e.currentTarget.value) })}>
                  {#each Array.from({ length: Math.max(tallest, cell.rowSpan) }, (_, i) => i + 1) as rows (rows)}
                    <option value={rows}>{rows === 1 ? '1 row' : `${rows} rows`}</option>
                  {/each}
                </select>
              </label>
            </div>

            <label class="field">Label
              <input value={cell.label ?? ''} maxlength="60"
                placeholder={kind === 'text' ? 'Text' : cellLabel({ ...cell, label: null }, scenesById, compositionsById, movesById)}
                onchange={(e) => update({ label: e.currentTarget.value.trim() || null })} />
            </label>

            <div class="colors">
              <label class="field">Color<input type="color" value={cell.color}
                onchange={(e) => update({ color: e.currentTarget.value })} /></label>
              <label class="field">Border<input type="color" value={cell.borderColor}
                onchange={(e) => update({ borderColor: e.currentTarget.value })} /></label>
            </div>

            {#if kind !== 'text'}
              {#if kind !== 'gradient' && kind !== 'pad'}
              <label class="field">Mode
                <select value={cell.mode} onchange={(e) => update({ mode: e.currentTarget.value as KeyMode })}>
                  <option value="toggle">Toggle – press on, press off</option>
                  <option value="flash">Flash – on while held</option>
                </select>
              </label>
              {/if}
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

    <p class="dim help">
      Pages of fields for party mode, shown as tabs. Each field switches a scene, a composition or a move on and
      off, shows a gradient to pick an intensity or a color by touch, a pad to point moving heads – or just shows
      a text. A field can be several columns wide and several rows high. Any number can be on at once; where they
      collide, the higher value wins. Fields of the same group exclude each other, also across pages (e.g. colors:
      blue goes off when red goes on). Click between two rows for a line.
    </p>
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

  .help {
    margin: 0;
    max-width: 820px;
    font-size: 12px;
  }

  .editor-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .pagebar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 12px;
  }

  .pages {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .pages button {
    min-height: 32px;
    padding: 0 14px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    cursor: pointer;
  }

  .pages button.active {
    border-color: var(--accent);
    color: var(--accent);
    background: var(--accent-soft);
  }

  .pages button.add {
    border-style: dashed;
  }

  .page-settings {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 14px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .page-settings input.name {
    width: 160px;
  }

  .sizes {
    display: flex;
    gap: 12px;
  }

  .sizes .field {
    flex: 1;
  }

  .page-settings input {
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

  .fixtures {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 220px;
    overflow-y: auto;
    margin: 0;
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .fixtures legend {
    padding: 0 4px;
    color: var(--text-dim);
    font-size: 12px;
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
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
