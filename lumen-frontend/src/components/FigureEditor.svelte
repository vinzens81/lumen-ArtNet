<script lang="ts">
  import { untrack } from 'svelte';
  import { api, ApiError } from '../lib/api';
  import {
    DMX_MAX, insertAfter, lineNear, reorder, snap, TEMPLATES, templatePoints, type Template,
  } from '../lib/moves';
  import { showError } from '../lib/toasts.svelte';
  import type { Figure, FigurePoint, Move } from '../lib/types';

  /**
   * Draws a figure in the pan/tilt grid (DMX values, tilt upwards): click adds a point at the end, dragging moves a
   * point, a click on a line inserts one there, Alt+click deletes. Shift snaps to the 16 grid. Saved a moment after
   * the last change; every move using the figure changes with it.
   */
  let { projectId, figure, moves = [], onSaved }: {
    projectId: number;
    figure: Figure;
    /** moves of the project, to say which use the figure */
    moves?: Move[];
    onSaved?: (figure: Figure) => void;
  } = $props();

  const SAVE_DELAY_MS = 350;
  /** how close (in DMX values) a click must be to grab a point or a line */
  const GRAB = 6;

  let draft = $state<Figure>(untrack(() => structuredClone($state.snapshot(figure)) as Figure));
  let saved = untrack(() => JSON.stringify(figure));
  let timer: ReturnType<typeof setTimeout> | null = null;
  let errors = $state<string[]>([]);
  let template = $state<Template>('circle');
  let templateCount = $state(8);
  let showNumbers = $state(true);
  let selected = $state<number | null>(null);
  let dragging = $state<number | null>(null);
  let svg: SVGSVGElement | undefined = $state();

  const usedBy = $derived(moves.filter((m) => m.figureId === figure.id));
  const lines = $derived(segments(draft.points, draft.closed));

  $effect(() => {
    const incoming = JSON.stringify(figure);
    if (draft.id !== figure.id || (timer === null && incoming !== saved)) {
      draft = structuredClone($state.snapshot(figure)) as Figure;
      saved = incoming;
      errors = [];
      selected = null;
    }
  });

  $effect(() => {
    const current = JSON.stringify(draft);
    if (current === saved || draft.id !== figure.id || dragging !== null) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(save, SAVE_DELAY_MS);
  });

  async function save() {
    timer = null;
    if (draft.points.length === 0) {
      errors = ['A figure needs at least one point'];
      return;
    }
    const snapshot = JSON.stringify(draft);
    try {
      const result = await api.figures.update(projectId, draft.id,
        { name: draft.name, closed: draft.closed, points: draft.points });
      errors = [];
      saved = JSON.stringify(result);
      if (JSON.stringify(draft) === snapshot) draft = structuredClone(result);
      onSaved?.(result);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) errors = e.errors.length > 0 ? e.errors : [e.message];
      else showError(e);
    }
  }

  function segments(points: FigurePoint[], closed: boolean) {
    const result: { a: FigurePoint; b: FigurePoint; closing: boolean }[] = [];
    for (let i = 1; i < points.length; i++) result.push({ a: points[i - 1], b: points[i], closing: false });
    if (closed && points.length > 2) result.push({ a: points[points.length - 1], b: points[0], closing: true });
    return result;
  }

  /** The DMX point under the pointer; tilt goes up. */
  function pointerPoint(event: PointerEvent): FigurePoint {
    const rect = svg!.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * DMX_MAX;
    const y = (1 - (event.clientY - rect.top) / rect.height) * DMX_MAX;
    return { x: snap(x, event.shiftKey), y: snap(y, event.shiftKey) };
  }

  function pointNear(p: FigurePoint): number | null {
    let best: { index: number; distance: number } | null = null;
    draft.points.forEach((q, index) => {
      const distance = Math.hypot(q.x - p.x, q.y - p.y);
      if (distance <= GRAB && (!best || distance < best.distance)) best = { index, distance };
    });
    return (best as { index: number } | null)?.index ?? null;
  }

  function onpointerdown(event: PointerEvent) {
    if (!svg) return;
    const p = pointerPoint(event);
    const hit = pointNear(p);
    if (hit !== null) {
      if (event.altKey) {
        removePoint(hit);
        return;
      }
      selected = hit;
      dragging = hit;
      svg.setPointerCapture(event.pointerId);
      return;
    }
    const line = lineNear(draft.points, draft.closed, p, GRAB);
    if (line !== null) {
      draft.points = insertAfter(draft.points, line, p);
      selected = line + 1;
    } else {
      draft.points = [...draft.points, p];
      selected = draft.points.length - 1;
    }
  }

  function onpointermove(event: PointerEvent) {
    if (dragging === null) return;
    draft.points[dragging] = pointerPoint(event);
  }

  function endDrag() {
    dragging = null;
  }

  function removePoint(index: number) {
    if (draft.points.length <= 1) {
      errors = ['A figure needs at least one point'];
      return;
    }
    draft.points = draft.points.filter((_, i) => i !== index);
    selected = null;
  }

  function applyTemplate() {
    draft.points = templatePoints(template, templateCount);
    draft.closed = TEMPLATES.find((t) => t.kind === template)?.closed ?? true;
    selected = null;
  }

  function clear() {
    // a figure keeps one point: the middle
    draft.points = [{ x: 127.5, y: 127.5 }];
    selected = 0;
  }

  function setCoordinate(index: number, axis: 'x' | 'y', value: number) {
    if (Number.isNaN(value)) return;
    draft.points[index] = { ...draft.points[index], [axis]: Math.min(DMX_MAX, Math.max(0, value)) };
  }

  function arrow(a: FigurePoint, b: FigurePoint) {
    const mx = (a.x + b.x) / 2;
    const my = DMX_MAX - (a.y + b.y) / 2;
    const angle = (Math.atan2(-(b.y - a.y), b.x - a.x) * 180) / Math.PI;
    return { mx, my, angle };
  }
</script>

<div class="figure-editor">
  <div class="toolbar">
    <label class="field name">Name<input bind:value={draft.name} /></label>
    <label class="field">Template
      <select bind:value={template}>
        {#each TEMPLATES as t (t.kind)}<option value={t.kind}>{t.label}</option>{/each}
      </select>
    </label>
    <label class="field count">Points<input type="number" min="1" max="64" bind:value={templateCount} /></label>
    <button class="btn small" type="button" onclick={applyTemplate}
      title="Replaces the points with the template; afterwards everything can be changed by hand">Apply</button>
    <label class="check"><input type="checkbox" bind:checked={draft.closed} /> Closed</label>
    <label class="check"><input type="checkbox" bind:checked={showNumbers} /> Show numbers</label>
    <button class="btn small ghost" type="button" onclick={clear}>Clear</button>
  </div>

  <div class="body">
    <div class="canvas-wrap">
      <svg bind:this={svg} viewBox="0 0 {DMX_MAX} {DMX_MAX}" class="canvas" role="application" tabindex="-1"
        aria-label="Figure: click adds a point, drag moves one, Alt+click deletes; exact values in the list of points"
        {onpointerdown} {onpointermove} onpointerup={endDrag} onpointercancel={endDrag}>
        {#each [64, 128, 192] as g (g)}
          <line class="guide" x1={g} y1="0" x2={g} y2={DMX_MAX} />
          <line class="guide" x1="0" y1={g} x2={DMX_MAX} y2={g} />
        {/each}
        {#each lines as line, i (i)}
          {@const a = arrow(line.a, line.b)}
          <line class="path" class:closing={line.closing} x1={line.a.x} y1={DMX_MAX - line.a.y} x2={line.b.x}
            y2={DMX_MAX - line.b.y} />
          <path class="arrow" d="M -3 -2.5 L 3 0 L -3 2.5 Z"
            transform="translate({a.mx} {a.my}) rotate({a.angle})" />
        {/each}
        {#each draft.points as p, i (i)}
          <circle class="point" class:selected={selected === i} class:first={i === 0} cx={p.x} cy={DMX_MAX - p.y}
            r="4" />
          {#if showNumbers}
            <text class="number" x={p.x + 6} y={DMX_MAX - p.y - 6}>{i + 1}</text>
          {/if}
        {/each}
      </svg>
      <div class="axes dim mono"><span>Pan →</span><span>Tilt ↑</span></div>
    </div>

    <div class="points">
      <h3>Points</h3>
      <ol>
        {#each draft.points as p, i (i)}
          <li class:selected={selected === i}>
            <button type="button" class="index mono" onclick={() => (selected = i)}
              aria-label="Select point {i + 1}">{i + 1}</button>
            <label class="coord">X<input type="number" min="0" max="255" step="1" value={p.x}
              onchange={(e) => setCoordinate(i, 'x', Number(e.currentTarget.value))} /></label>
            <label class="coord">Y<input type="number" min="0" max="255" step="1" value={p.y}
              onchange={(e) => setCoordinate(i, 'y', Number(e.currentTarget.value))} /></label>
            <span class="actions">
              <button class="btn small ghost" type="button" onclick={() => (draft.points = reorder(draft.points, i, -1))}
                aria-label="Move point {i + 1} up">↑</button>
              <button class="btn small ghost" type="button" onclick={() => (draft.points = reorder(draft.points, i, 1))}
                aria-label="Move point {i + 1} down">↓</button>
              <button class="btn small ghost danger" type="button" onclick={() => removePoint(i)}
                aria-label="Delete point {i + 1}">✕</button>
            </span>
          </li>
        {/each}
      </ol>
      <p class="hint dim">
        {draft.points.length === 1 ? 'One point is a fixed position.' : 'The numbers matter for the offset of the fixtures.'}
        Shift snaps to the 16 grid.
      </p>
      {#if usedBy.length > 0}
        <p class="hint dim">Used by {usedBy.map((m) => `“${m.name}”`).join(', ')} – they change with it.</p>
      {/if}
    </div>
  </div>
  {#each errors as error, i (i)}<p class="error">{error}</p>{/each}
</div>

<style>
  .figure-editor {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 10px;
  }

  .toolbar .name {
    flex: 1;
    min-width: 160px;
  }

  .toolbar .count input {
    width: 64px;
  }

  .body {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 16px;
    align-items: start;
  }

  .canvas-wrap {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .canvas {
    width: 100%;
    max-width: 560px;
    aspect-ratio: 1;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background:
      linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 6.25% 6.25%,
      linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 6.25% 6.25%,
      rgba(0, 0, 0, 0.3);
    touch-action: none;
    cursor: crosshair;
  }

  .axes {
    display: flex;
    justify-content: space-between;
    max-width: 560px;
    font-size: 11px;
  }

  .guide {
    stroke: rgba(255, 255, 255, 0.12);
    stroke-width: 0.5;
  }

  .path {
    stroke: var(--accent);
    stroke-width: 1.5;
  }

  .path.closing {
    stroke-dasharray: 3 3;
    opacity: 0.7;
  }

  .arrow {
    fill: var(--accent);
  }

  .point {
    fill: var(--panel-solid);
    stroke: var(--accent);
    stroke-width: 1.5;
  }

  .point.first {
    fill: var(--accent);
  }

  .point.selected {
    stroke: var(--warning);
    stroke-width: 2.5;
  }

  .number {
    fill: var(--text);
    font-size: 8px;
    font-family: var(--mono);
    pointer-events: none;
  }

  .points h3 {
    margin: 0 0 6px;
  }

  ol {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
    max-height: 420px;
    overflow-y: auto;
  }

  li {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 4px;
    border-radius: var(--radius-sm);
  }

  li.selected {
    background: var(--accent-soft);
  }

  .index {
    width: 26px;
    padding: 2px;
    border: 0;
    background: none;
    color: var(--text-dim);
    font: inherit;
    cursor: pointer;
  }

  .coord {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .coord input {
    width: 58px;
  }

  .actions {
    display: flex;
    margin-left: auto;
  }

  .hint {
    margin: 8px 0 0;
    font-size: 12px;
  }

  .error {
    margin: 0;
    color: var(--danger);
  }

  @media (max-width: 900px) {
    .body {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
