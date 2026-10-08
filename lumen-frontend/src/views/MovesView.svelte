<script lang="ts">
  import FigureEditor from '../components/FigureEditor.svelte';
  import Modal from '../components/Modal.svelte';
  import MoveEditor from '../components/MoveEditor.svelte';
  import { api } from '../lib/api';
  import { modesOf } from '../lib/color';
  import { focus } from '../lib/focus.svelte';
  import { live } from '../lib/live.svelte';
  import { canMove, newMove, templatePoints } from '../lib/moves';
  import { programmerSelection } from '../lib/selection.svelte';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Figure, Fixture, Move, Scene } from '../lib/types';

  /** What is open on the right: a move or a figure. */
  type Selection = { kind: 'move' | 'figure'; id: number } | null;

  let moves = $state<Move[]>([]);
  let figures = $state<Figure[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let scenes = $state<Scene[]>([]);
  let selection = $state<Selection>(null);
  let createOpen = $state<'move' | 'figure' | null>(null);
  let deleteOpen = $state(false);
  let form = $state({ name: '', figureId: '', fixtureIds: [] as number[] });

  const projectId = $derived(app.active?.id ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const movable = $derived(fixtures.filter((f) => canMove(modes.get(f.id))));
  const selectedMove = $derived(selection?.kind === 'move' ? moves.find((m) => m.id === selection!.id) ?? null : null);
  const selectedFigure = $derived(selection?.kind === 'figure'
    ? figures.find((f) => f.id === selection!.id) ?? null : null);
  const figuresById = $derived(new Map(figures.map((f) => [f.id, f])));

  $effect(() => {
    void live.movesRevision;
    void live.scenesRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [moves, figures, fixtures, scenes] = await Promise.all([
        api.moves.list(id),
        api.figures.list(id),
        api.fixtures.list(id),
        api.scenes.list(id),
      ]);
      if (focus.moveId !== null && moves.some((m) => m.id === focus.moveId)) {
        selection = { kind: 'move', id: focus.moveId };
        focus.moveId = null;
      }
      const exists = selection?.kind === 'move' ? moves.some((m) => m.id === selection!.id)
        : selection?.kind === 'figure' ? figures.some((f) => f.id === selection!.id) : false;
      if (!exists) {
        selection = moves[0] ? { kind: 'move', id: moves[0].id }
          : figures[0] ? { kind: 'figure', id: figures[0].id } : null;
      }
    } catch (e) {
      showError(e);
    }
  }

  function openCreate(kind: 'move' | 'figure') {
    const selected = programmerSelection.ids.filter((id) => movable.some((f) => f.id === id));
    form = { name: '', figureId: figures[0] ? String(figures[0].id) : '', fixtureIds: selected };
    createOpen = kind;
  }

  async function create() {
    if (projectId === null) return;
    try {
      if (createOpen === 'figure') {
        const figure = await api.figures.create(projectId,
          { name: form.name || 'Figure', closed: true, points: templatePoints('circle', 8) });
        figures = [...figures, figure].sort((a, b) => a.name.localeCompare(b.name));
        selection = { kind: 'figure', id: figure.id };
      } else {
        // without a figure yet, the move gets a circle of its own
        const figureId = form.figureId ? Number(form.figureId) : (await api.figures.create(projectId,
          { name: form.name || 'Circle', closed: true, points: templatePoints('circle', 8) })).id;
        const move = await api.moves.create(projectId, newMove(form.name || 'Move', figureId, form.fixtureIds));
        selection = { kind: 'move', id: move.id };
        await load(projectId);
      }
      createOpen = null;
    } catch (e) {
      showError(e);
    }
  }

  async function duplicate() {
    if (projectId === null || !selection) return;
    try {
      if (selection.kind === 'move') {
        const copy = await api.moves.duplicate(projectId, selection.id);
        selection = { kind: 'move', id: copy.id };
        notify(`Copied to “${copy.name}”`);
      } else {
        const copy = await api.figures.duplicate(projectId, selection.id);
        selection = { kind: 'figure', id: copy.id };
        notify(`Copied to “${copy.name}”`);
      }
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (projectId === null || !selection) return;
    const current = selection;
    const name = current.kind === 'move' ? selectedMove?.name : selectedFigure?.name;
    try {
      if (current.kind === 'move') await api.moves.remove(projectId, current.id);
      else await api.figures.remove(projectId, current.id);
      notify(`Deleted “${name}”`);
      deleteOpen = false;
      selection = null;
      await load(projectId);
    } catch (e) {
      deleteOpen = false;
      showError(e);
    }
  }

  function toggleFixture(id: number, on: boolean) {
    form.fixtureIds = on ? [...form.fixtureIds, id] : form.fixtureIds.filter((f) => f !== id);
  }

  function moveSubtitle(move: Move): string {
    const figure = figuresById.get(move.figureId);
    const heads = `${move.fixtures.length} ${move.fixtures.length === 1 ? 'head' : 'heads'}`;
    if (!figure) return heads;
    return figure.points.length === 1 ? `Position · ${heads}` : `${figure.name} · ${heads}`;
  }

  function figureSubtitle(figure: Figure): string {
    const users = moves.filter((m) => m.figureId === figure.id).length;
    const points = `${figure.points.length} ${figure.points.length === 1 ? 'point' : 'points'}`;
    return `${points}${figure.closed && figure.points.length > 1 ? ' · closed' : ''}${users > 0 ? ` · ${users} ${users === 1 ? 'move' : 'moves'}` : ''}`;
  }

  /** A small drawing of the figure for the list. */
  function thumbnail(figure: Figure | undefined): string {
    if (!figure || figure.points.length === 0) return '';
    const points = figure.closed && figure.points.length > 2 ? [...figure.points, figure.points[0]] : figure.points;
    return points.map((p) => `${(p.x / 255) * 24},${24 - (p.y / 255) * 24}`).join(' ');
  }

  function savedMove(move: Move) {
    moves = moves.map((m) => (m.id === move.id ? move : m));
  }

  function savedFigure(figure: Figure) {
    figures = figures.map((f) => (f.id === figure.id ? figure : f));
  }
</script>

<section class="view">
  <header class="head">
    <div>
      <h1>Moves</h1>
      <p class="dim">
        Where the moving heads point. A figure is a shape in the pan/tilt grid; a move lays it onto fixtures with size,
        shift, tempo and offset. One point is a fixed position. Scenes, light markers, keys and the party matrix play
        moves next to looks – looks no longer hold pan and tilt.
      </p>
    </div>
    <div class="head-actions">
      <button class="btn" type="button" onclick={() => openCreate('figure')} disabled={projectId === null}>
        + New figure</button>
      <button class="btn primary" type="button" onclick={() => openCreate('move')} disabled={projectId === null}>
        + New move</button>
    </div>
  </header>

  {#if !app.active}
    <div class="glass empty">Activate a project first.</div>
  {:else if moves.length === 0 && figures.length === 0}
    <div class="glass empty">
      <h2>No moves yet</h2>
      <p>
        Create a move for the moving heads: it starts with a circle you can change. For a fixed position, set pan and
        tilt in the programmer and use “Record position” there.
      </p>
      <button class="btn primary" type="button" onclick={() => openCreate('move')}>+ New move</button>
    </div>
  {:else}
    <div class="layout">
      <nav class="glass list" aria-label="Moves and figures">
        <h2 class="group">Moves</h2>
        {#each moves as move (move.id)}
          {@const onStage = stagePreview.status.moveId === move.id}
          <button type="button" class="item" class:active={selection?.kind === 'move' && selection.id === move.id}
            class:on-stage={onStage} onclick={() => (selection = { kind: 'move', id: move.id })}>
            <svg class="thumb" viewBox="-2 -2 28 28" aria-hidden="true">
              <polyline points={thumbnail(figuresById.get(move.figureId))} />
            </svg>
            <span class="text">
              <span class="name">{move.name}</span>
              <span class="dim small">{moveSubtitle(move)}</span>
            </span>
            {#if onStage}<span class="on-stage-tag">ON STAGE</span>{/if}
          </button>
        {:else}
          <p class="dim small empty-group">No moves yet.</p>
        {/each}
        <h2 class="group">Figures</h2>
        {#each figures as figure (figure.id)}
          <button type="button" class="item" class:active={selection?.kind === 'figure' && selection.id === figure.id}
            onclick={() => (selection = { kind: 'figure', id: figure.id })}>
            <svg class="thumb" viewBox="-2 -2 28 28" aria-hidden="true">
              <polyline points={thumbnail(figure)} />
            </svg>
            <span class="text">
              <span class="name">{figure.name}</span>
              <span class="dim small">{figureSubtitle(figure)}</span>
            </span>
          </button>
        {/each}
      </nav>

      <article class="glass panel">
        {#if selectedMove && projectId !== null}
          {#key selectedMove.id}
            <MoveEditor {projectId} move={selectedMove} {figures} {fixtures} {modes} {scenes} onSaved={savedMove}
              onEditFigure={(id) => (selection = { kind: 'figure', id })} />
          {/key}
        {:else if selectedFigure && projectId !== null}
          {#key selectedFigure.id}
            <FigureEditor {projectId} figure={selectedFigure} {moves} onSaved={savedFigure} />
          {/key}
        {:else}
          <p class="dim">Select a move or a figure.</p>
        {/if}
        {#if selection}
          <footer class="footer">
            <button class="btn small ghost" type="button" onclick={duplicate}>⧉ Duplicate</button>
            <button class="btn small ghost danger" type="button" onclick={() => (deleteOpen = true)}>Delete</button>
          </footer>
        {/if}
      </article>
    </div>
  {/if}
</section>

<Modal bind:open={() => createOpen !== null, (open) => { if (!open) createOpen = null; }}
  title={createOpen === 'figure' ? 'New figure' : 'New move'}>
  <form id="move-form" onsubmit={(e) => { e.preventDefault(); create(); }}>
    <label class="field">Name<input bind:value={form.name} placeholder={createOpen === 'figure' ? 'Eight' : 'Eight above the stage'} /></label>
    {#if createOpen === 'move'}
      <label class="field">Figure
        <select bind:value={form.figureId}>
          <option value="">A new circle</option>
          {#each figures as figure (figure.id)}<option value={String(figure.id)}>{figure.name}</option>{/each}
        </select>
      </label>
      <fieldset class="fixtures">
        <legend>Fixtures</legend>
        {#each movable as fixture (fixture.id)}
          <label class="check"><input type="checkbox" checked={form.fixtureIds.includes(fixture.id)}
            onchange={(e) => toggleFixture(fixture.id, e.currentTarget.checked)} /> {fixture.name}</label>
        {:else}
          <p class="dim small">No fixtures with pan and tilt in the patch.</p>
        {/each}
      </fieldset>
    {:else}
      <p class="dim small">Starts as a circle with 8 points – change it with a template or by hand.</p>
    {/if}
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (createOpen = null)}>Cancel</button>
    <button class="btn primary" type="submit" form="move-form">Create</button>
  {/snippet}
</Modal>

<Modal bind:open={deleteOpen} title={selection?.kind === 'figure' ? 'Delete figure' : 'Delete move'}>
  <p>Delete <strong>{selection?.kind === 'figure' ? selectedFigure?.name : selectedMove?.name}</strong>?</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (deleteOpen = false)}>Cancel</button>
    <button class="btn danger" type="button" onclick={remove}>Delete</button>
  {/snippet}
</Modal>

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
    max-width: 680px;
  }

  .head-actions {
    display: flex;
    gap: 8px;
  }

  .layout {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr);
    gap: 12px;
    align-items: start;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px;
  }

  .group {
    margin: 8px 10px 4px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .empty-group {
    margin: 0 10px 6px;
  }

  .item {
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

  .item:hover {
    background: var(--panel-raised);
  }

  .item.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .item.on-stage {
    box-shadow: inset 3px 0 0 var(--accent), -4px 0 14px -6px var(--accent-glow);
  }

  .on-stage-tag {
    padding: 2px 6px;
    border-radius: 6px;
    background: var(--accent);
    color: var(--accent-ink);
    font-size: 9px;
    font-weight: 800;
    letter-spacing: 0.1em;
    white-space: nowrap;
  }

  .thumb {
    flex: none;
    width: 28px;
    height: 28px;
  }

  .thumb polyline {
    fill: none;
    stroke: var(--accent);
    stroke-width: 1.5;
    stroke-linejoin: round;
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .panel {
    padding: 18px;
  }

  .footer {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .fixtures {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 10px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .fixtures legend {
    padding: 0 4px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .small {
    font-size: 12px;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
