<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import { api, ApiError } from '../lib/api';
  import {
    canMove, DMX_MAX, isFixedPosition, movePosition, noCorrection, outside, pathOf, placedFigure, startPoint,
    type PositionLimits,
  } from '../lib/moves';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import { showError } from '../lib/toasts.svelte';
  import type { Figure, Fixture, FigurePoint, Mode, Move, MoveFixture, MoveRepeat, OnceStart, Scene } from '../lib/types';
  import XYPad from './XYPad.svelte';

  /**
   * A move: a figure laid onto pan/tilt fixtures with size, shift, rotation, tempo, repeat and offset, plus the own
   * corrections of each fixture. The preview runs in the browser only; "Show on stage" moves the real heads. Saved a
   * moment after the last change.
   */
  let {
    projectId,
    move,
    figures,
    fixtures,
    modes,
    scenes = [],
    limits = new Map(),
    onEditFigure,
    onSaved,
  }: {
    projectId: number;
    move: Move;
    figures: Figure[];
    fixtures: Fixture[];
    modes: Map<number, Mode>;
    scenes?: Scene[];
    /** allowed range of pan and tilt per fixture id, in DMX values */
    limits?: Map<number, PositionLimits>;
    onEditFigure?: (figureId: number) => void;
    onSaved?: (move: Move) => void;
  } = $props();

  const SAVE_DELAY_MS = 350;
  /** shift range of the small pad per fixture, in DMX values to each side */
  const PAD_RANGE = 128;

  let draft = $state<Move>(untrack(() => structuredClone($state.snapshot(move)) as Move));
  let saved = untrack(() => JSON.stringify(move));
  let timer: ReturnType<typeof setTimeout> | null = null;
  let errors = $state<string[]>([]);
  let addFixture = $state('');
  let playing = $state(true);
  let seconds = $state(0);
  let padFor = $state<number | null>(null);
  let selectedFixture = $state<number | null>(null);
  let svg: SVGSVGElement | undefined = $state();
  let drag: { kind: 'shift' | 'size'; from: FigurePoint; start: Move } | null = null;

  const figure = $derived(figures.find((f) => f.id === draft.figureId));
  const path = $derived(pathOf(figure?.points ?? [], figure?.closed ?? true));
  const placed = $derived(placedFigure(draft, path));
  const box = $derived(bounds(placed));
  const fixed = $derived(isFixedPosition(figure));
  const fixturesById = $derived(new Map(fixtures.map((f) => [f.id, f])));
  const movable = $derived(fixtures.filter((f) => canMove(modes.get(f.id)) && !draft.fixtures.some((m) => m.fixtureId === f.id)));
  const heads = $derived(draft.fixtures.map((_, i) => movePosition(draft, path, seconds, i)));
  const frame = $derived(selectedFixture !== null ? limits.get(selectedFixture) ?? null : null);
  const onStage = $derived(stagePreview.status.moveId === move.id);
  const usedIn = $derived(scenes.filter((s) => s.moves.some((u) => u.moveId === move.id)
    || s.sections.some((part) => part.moves.some((u) => u.moveId === move.id))
    || s.keys.some((k) => k.moveId === move.id)));

  $effect(() => {
    const incoming = JSON.stringify(move);
    if (draft.id !== move.id || (timer === null && incoming !== saved)) {
      draft = structuredClone($state.snapshot(move)) as Move;
      saved = incoming;
      errors = [];
    }
  });

  $effect(() => {
    const current = JSON.stringify(draft);
    if (current === saved || draft.id !== move.id) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(save, SAVE_DELAY_MS);
  });

  // the preview clock, in the browser only
  let frameRequest = 0;
  let last = performance.now();
  function tick(now: number) {
    if (playing) seconds += (now - last) / 1000;
    last = now;
    frameRequest = requestAnimationFrame(tick);
  }
  frameRequest = requestAnimationFrame(tick);
  onDestroy(() => cancelAnimationFrame(frameRequest));

  async function save() {
    timer = null;
    const snapshot = JSON.stringify(draft);
    try {
      const { id: _, ...request } = $state.snapshot(draft) as Move;
      const result = await api.moves.update(projectId, draft.id, request);
      errors = [];
      saved = JSON.stringify(result);
      if (JSON.stringify(draft) === snapshot) draft = structuredClone(result);
      onSaved?.(result);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) errors = e.errors.length > 0 ? e.errors : [e.message];
      else showError(e);
    }
  }

  function bounds(points: FigurePoint[]) {
    if (points.length === 0) return { minX: 0, minY: 0, maxX: 0, maxY: 0 };
    const xs = points.map((p) => p.x);
    const ys = points.map((p) => p.y);
    return { minX: Math.min(...xs), minY: Math.min(...ys), maxX: Math.max(...xs), maxY: Math.max(...ys) };
  }

  function add() {
    const id = Number(addFixture);
    addFixture = '';
    if (id) draft.fixtures = [...draft.fixtures, noCorrection(id)];
  }

  function changeFixture(index: number, patch: Partial<MoveFixture>) {
    draft.fixtures[index] = { ...draft.fixtures[index], ...patch };
  }

  function reorderFixture(index: number, step: number) {
    const other = index + step;
    if (other < 0 || other >= draft.fixtures.length) return;
    const next = [...draft.fixtures];
    [next[index], next[other]] = [next[other], next[index]];
    draft.fixtures = next;
  }

  function label(fixtureId: number): string {
    const fixture = fixturesById.get(fixtureId);
    return fixture?.shortName ?? fixture?.name ?? `#${fixtureId}`;
  }

  function pointerPoint(event: PointerEvent): FigurePoint {
    const rect = svg!.getBoundingClientRect();
    return { x: ((event.clientX - rect.left) / rect.width) * DMX_MAX,
      y: (1 - (event.clientY - rect.top) / rect.height) * DMX_MAX };
  }

  /** Dragging the figure shifts it, the corner handle changes its size. */
  function onpointerdown(event: PointerEvent) {
    if (!svg) return;
    const p = pointerPoint(event);
    const nearCorner = Math.hypot(p.x - box.maxX, p.y - box.minY) <= 8;
    const inside = p.x >= box.minX - 6 && p.x <= box.maxX + 6 && p.y >= box.minY - 6 && p.y <= box.maxY + 6;
    if (!nearCorner && !inside) return;
    drag = { kind: nearCorner ? 'size' : 'shift', from: p, start: structuredClone($state.snapshot(draft)) as Move };
    svg.setPointerCapture(event.pointerId);
  }

  function onpointermove(event: PointerEvent) {
    if (!drag) return;
    const p = pointerPoint(event);
    const dx = p.x - drag.from.x;
    const dy = p.y - drag.from.y;
    if (drag.kind === 'shift') {
      draft.shiftX = clampShift(Math.round(drag.start.shiftX + dx));
      draft.shiftY = clampShift(Math.round(drag.start.shiftY + dy));
    } else {
      // the size follows the distance of the handle from the middle of the placed figure
      const cx = path.center.x + drag.start.shiftX;
      const cy = path.center.y + drag.start.shiftY;
      const fromX = Math.abs(drag.from.x - cx) || 1;
      const fromY = Math.abs(drag.from.y - cy) || 1;
      draft.sizeX = clampSize(Math.round((drag.start.sizeX * Math.abs(p.x - cx)) / fromX));
      draft.sizeY = clampSize(Math.round((drag.start.sizeY * Math.abs(p.y - cy)) / fromY));
    }
  }

  function endDrag() {
    drag = null;
  }

  function clampShift(value: number): number {
    return Math.max(-DMX_MAX, Math.min(DMX_MAX, value));
  }

  function clampSize(value: number): number {
    return Math.max(0, Math.min(400, value));
  }

  function polyline(points: FigurePoint[], closed: boolean): string {
    const all = closed && points.length > 2 ? [...points, points[0]] : points;
    return all.map((p) => `${p.x},${DMX_MAX - p.y}`).join(' ');
  }

  async function toggleStage() {
    if (onStage) {
      await stagePreview.stop();
    } else {
      if (timer) {
        clearTimeout(timer);
        await save();
      }
      await stagePreview.showMove(projectId, move.id);
    }
  }

  function number(event: Event): number {
    return Number((event.currentTarget as HTMLInputElement).value);
  }
</script>

<div class="move-editor">
  <div class="row">
    <label class="field grow">Name<input bind:value={draft.name} /></label>
    <label class="field grow">Figure
      <select value={String(draft.figureId)} onchange={(e) => (draft.figureId = Number(e.currentTarget.value))}>
        {#each figures as f (f.id)}
          <option value={String(f.id)}>{f.name} ({f.points.length} {f.points.length === 1 ? 'point' : 'points'})</option>
        {/each}
      </select>
    </label>
    {#if onEditFigure}
      <button class="btn small" type="button" onclick={() => onEditFigure(draft.figureId)}>Edit figure</button>
    {/if}
  </div>

  <div class="body">
    <div class="canvas-wrap">
      <svg bind:this={svg} viewBox="0 0 {DMX_MAX} {DMX_MAX}" class="canvas" role="application" tabindex="-1"
        aria-label="Preview: drag the figure to shift it, the corner handle to change its size"
        {onpointerdown} {onpointermove} onpointerup={endDrag} onpointercancel={endDrag}>
        {#each [64, 128, 192] as g (g)}
          <line class="guide" x1={g} y1="0" x2={g} y2={DMX_MAX} />
          <line class="guide" x1="0" y1={g} x2={DMX_MAX} y2={g} />
        {/each}
        {#if frame}
          <rect class="limits" x={frame.panMin} y={DMX_MAX - frame.tiltMax} width={frame.panMax - frame.panMin}
            height={frame.tiltMax - frame.tiltMin} />
        {/if}
        {#if figure}
          <polyline class="base" points={polyline(figure.points, path.closed)} />
          <polyline class="placed" points={polyline(placed, path.closed)} />
          {#each placed as p, i (i)}
            <circle class="corner" class:outside={outside(p, frame)} cx={p.x} cy={DMX_MAX - p.y} r="2.5" />
          {/each}
          {#if !fixed}
            <rect class="handle" x={box.maxX - 3} y={DMX_MAX - box.minY - 3} width="6" height="6" />
          {/if}
        {/if}
        {#each heads as head, i (draft.fixtures[i].fixtureId)}
          <g class="head" class:selected={selectedFixture === draft.fixtures[i].fixtureId}
            transform="translate({head.x} {DMX_MAX - head.y})">
            <circle r="5" />
            <text x="7" y="-6">{label(draft.fixtures[i].fixtureId)}</text>
          </g>
        {/each}
      </svg>
      <div class="canvas-actions">
        <button class="btn small" type="button" onclick={() => (playing = !playing)} disabled={fixed}>
          {playing ? '⏸ Pause' : '▶ Preview'}</button>
        <button class="btn small ghost" type="button" onclick={() => (seconds = 0)} disabled={fixed}>⟲ Restart</button>
        <button class="btn small" class:primary={!onStage} type="button" onclick={toggleStage}
          disabled={draft.fixtures.length === 0}
          title="Moves the real heads on stage, above the playback">{onStage ? '■ Stop' : '▶ Show on stage'}</button>
      </div>
    </div>

    <div class="settings">
      <div class="grid">
        <span class="label">Size</span>
        <label class="mini">X %<input type="number" min="0" max="400" step="5" bind:value={draft.sizeX} /></label>
        <label class="mini">Y %<input type="number" min="0" max="400" step="5" bind:value={draft.sizeY} /></label>
        <span class="label">Shift</span>
        <label class="mini">X<input type="number" min="-255" max="255" step="1" bind:value={draft.shiftX} /></label>
        <label class="mini">Y<input type="number" min="-255" max="255" step="1" bind:value={draft.shiftY} /></label>
        <span class="label">Rotate</span>
        <label class="mini">°<input type="number" min="-360" max="360" step="5" bind:value={draft.rotation} /></label>
        <span></span>
        {#if !fixed}
          <span class="label">Time</span>
          <label class="mini">s per round<input type="number" min="0.1" max="3600" step="0.5"
            value={draft.durationMs / 1000}
            onchange={(e) => (draft.durationMs = Math.max(100, Math.round(number(e) * 1000)))} /></label>
          <span></span>
          <span class="label">Repeat</span>
          <div class="segmented" role="group" aria-label="Repeat">
            {#each [['loop', 'Loop'], ['once', 'Once'], ['ping_pong', 'Ping-pong']] as [value, text] (value)}
              <button type="button" class:active={draft.repeat === value}
                onclick={() => (draft.repeat = value as MoveRepeat)}>{text}</button>
            {/each}
          </div>
          <span></span>
          <span class="label">Offset</span>
          <label class="mini">steps per fixture<input type="number" min="0" max="1000" step="1"
            bind:value={draft.stepOffset} /></label>
          <span></span>
          {#if draft.repeat === 'once' && draft.stepOffset > 0}
            <span class="label">Start</span>
            <div class="segmented" role="group" aria-label="Start when running once">
              {#each [['sequential', 'One after another'], ['together', 'Together']] as [value, text] (value)}
                <button type="button" class:active={draft.onceStart === value}
                  onclick={() => (draft.onceStart = value as OnceStart)}>{text}</button>
              {/each}
            </div>
            <span></span>
          {/if}
        {/if}
      </div>

      <h3>Fixtures</h3>
      {#if draft.fixtures.length === 0}
        <p class="dim small">No fixtures yet – add the moving heads that follow the figure.</p>
      {/if}
      <ol class="fixtures">
        {#each draft.fixtures as fixture, i (fixture.fixtureId)}
          <li class:selected={selectedFixture === fixture.fixtureId}>
            <div class="fixture-head">
              <button type="button" class="link" onclick={() => (selectedFixture =
                selectedFixture === fixture.fixtureId ? null : fixture.fixtureId)}
                title="Shows the allowed range of the fixture">{label(fixture.fixtureId)}</button>
              {#if !fixed}<span class="dim small" title="The point the fixture starts at">starts at {startPoint(
                draft.stepOffset, figure?.points.length ?? 0, i)}</span>{/if}
              <span class="actions">
                <button class="btn small ghost" type="button" onclick={() => reorderFixture(i, -1)}
                  aria-label="Move {label(fixture.fixtureId)} up">↑</button>
                <button class="btn small ghost" type="button" onclick={() => reorderFixture(i, 1)}
                  aria-label="Move {label(fixture.fixtureId)} down">↓</button>
                <button class="btn small ghost danger" type="button"
                  onclick={() => (draft.fixtures = draft.fixtures.filter((_, j) => j !== i))}
                  aria-label="Remove {label(fixture.fixtureId)}">✕</button>
              </span>
            </div>
            <div class="fixture-settings">
              <label class="mini">Shift X<input type="number" min="-255" max="255" step="1" value={fixture.shiftX}
                onchange={(e) => changeFixture(i, { shiftX: number(e) })} /></label>
              <label class="mini">Shift Y<input type="number" min="-255" max="255" step="1" value={fixture.shiftY}
                onchange={(e) => changeFixture(i, { shiftY: number(e) })} /></label>
              <button class="btn small ghost" type="button" onclick={() => (padFor = padFor === i ? null : i)}
                title="Adjust the own shift with a pad while the head shows the figure on stage">⊹</button>
              <label class="check small"><input type="checkbox" checked={fixture.mirrorX}
                onchange={(e) => changeFixture(i, { mirrorX: e.currentTarget.checked })} /> Mirror X</label>
              <label class="check small"><input type="checkbox" checked={fixture.mirrorY}
                onchange={(e) => changeFixture(i, { mirrorY: e.currentTarget.checked })} /> Mirror Y</label>
              {#if !fixed}
                <label class="check small"><input type="checkbox" checked={fixture.reverse}
                  onchange={(e) => changeFixture(i, { reverse: e.currentTarget.checked })} /> Reverse</label>
              {/if}
            </div>
            {#if padFor === i}
              <div class="pad">
                <XYPad label="Own shift of {label(fixture.fixtureId)}"
                  x={(fixture.shiftX + PAD_RANGE) / (2 * PAD_RANGE)} y={(fixture.shiftY + PAD_RANGE) / (2 * PAD_RANGE)}
                  onchange={(x, y) => changeFixture(i, { shiftX: Math.round(x * 2 * PAD_RANGE - PAD_RANGE),
                    shiftY: Math.round(y * 2 * PAD_RANGE - PAD_RANGE) })} />
              </div>
            {/if}
          </li>
        {/each}
      </ol>
      <label class="field">
        <span class="sr-only">Add a fixture</span>
        <select bind:value={addFixture} onchange={add} disabled={movable.length === 0}>
          <option value="">{movable.length === 0 ? 'No other fixtures with pan and tilt' : '+ Add a fixture…'}</option>
          {#each movable as f (f.id)}<option value={String(f.id)}>{f.name}</option>{/each}
        </select>
      </label>
      {#if usedIn.length > 0}
        <p class="hint dim">Played in scene {usedIn.map((s) => s.number).join(', ')}.</p>
      {/if}
    </div>
  </div>
  {#each errors as error, i (i)}<p class="error">{error}</p>{/each}
</div>

<style>
  .move-editor {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .row {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 10px;
  }

  .grow {
    flex: 1;
    min-width: 160px;
  }

  .body {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(300px, 380px);
    gap: 16px;
    align-items: start;
  }

  .canvas-wrap {
    display: flex;
    flex-direction: column;
    gap: 8px;
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
    cursor: move;
  }

  .canvas-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .guide {
    stroke: rgba(255, 255, 255, 0.12);
    stroke-width: 0.5;
  }

  .limits {
    fill: none;
    stroke: var(--warning);
    stroke-width: 1;
    stroke-dasharray: 4 2;
  }

  .base {
    fill: none;
    stroke: var(--text-faint);
    stroke-width: 1;
    stroke-dasharray: 2 2;
  }

  .placed {
    fill: none;
    stroke: var(--accent);
    stroke-width: 1.5;
  }

  .corner {
    fill: var(--accent);
  }

  .corner.outside {
    fill: var(--danger);
  }

  .handle {
    fill: var(--panel-solid);
    stroke: var(--accent);
    stroke-width: 1;
    cursor: nwse-resize;
  }

  .head circle {
    fill: var(--warning);
    stroke: #fff;
    stroke-width: 1;
  }

  .head.selected circle {
    fill: #fff;
  }

  .head text {
    fill: var(--text);
    font-size: 8px;
    font-family: var(--mono);
  }

  .settings {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .settings h3 {
    margin: 6px 0 0;
  }

  .grid {
    display: grid;
    grid-template-columns: 60px 1fr 1fr;
    gap: 6px 8px;
    align-items: end;
  }

  .label {
    padding-bottom: 6px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .mini {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 11px;
    color: var(--text-dim);
  }

  .mini input {
    width: 100%;
  }

  .segmented {
    grid-column: span 2;
    display: flex;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    overflow: hidden;
  }

  .segmented button {
    flex: 1;
    padding: 6px 4px;
    border: 0;
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .segmented button.active {
    background: var(--accent-soft);
    color: var(--accent);
  }

  .fixtures {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .fixtures li {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
  }

  .fixtures li.selected {
    box-shadow: inset 0 0 0 1px var(--warning);
  }

  .fixture-head,
  .fixture-settings {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .fixture-settings .mini {
    width: 70px;
  }

  .link {
    padding: 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
  }

  .link:hover {
    color: var(--accent);
  }

  .actions {
    display: flex;
    margin-left: auto;
  }

  .pad {
    max-width: 200px;
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .small {
    font-size: 11px;
  }

  .error {
    margin: 0;
    color: var(--danger);
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }

  @media (max-width: 1000px) {
    .body {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
