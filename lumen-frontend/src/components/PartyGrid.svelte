<script lang="ts">
  import {
    cellLabel, faderBackground, faderPoint, gridRow, isOn, padPointAt, pagePlaces, playsSomething, pointAt, rowTracks,
    textColorFor,
  } from '../lib/party';
  import Modal from './Modal.svelte';
  import XYPad from './XYPad.svelte';
  import type { Composition, Move, PartyCell, PartyPage, PartyStatus, Scene } from '../lib/types';

  let {
    page,
    pageIndex = 0,
    scenes,
    compositions,
    moves = new Map(),
    editing = false,
    fill = false,
    status = null,
    selected = null,
    onselect,
    ontoggleseparator,
    onpress,
    onrelease,
    onslide,
  }: {
    page: PartyPage;
    /** which page of the matrix this is, for the status of its fields */
    pageIndex?: number;
    scenes: Map<number, Scene>;
    compositions: Map<number, Composition>;
    moves?: Map<number, Move>;
    /** setup: every place can be selected, lines between rows can be switched */
    editing?: boolean;
    /** take the room given and share it among the rows (party mode: no scrolling) */
    fill?: boolean;
    /** party mode: which fields are on */
    status?: PartyStatus | null;
    selected?: { row: number; column: number } | null;
    onselect?: (row: number, column: number) => void;
    ontoggleseparator?: (row: number) => void;
    onpress?: (cell: PartyCell) => void;
    onrelease?: (cell: PartyCell) => void;
    /** party mode: a gradient or pad touched at a point (0–1, y from the bottom; a gradient keeps y at 0.5) */
    onslide?: (cell: PartyCell, x: number, y: number) => void;
  } = $props();

  /** keyboard steps on a gradient or pad */
  const KEY_STEP = 0.05;
  /** from this many columns on, fields get smaller text */
  const DENSE_COLUMNS = 9;
  /** two touches on a pad within this time (ms) and distance (px) open it large */
  const DOUBLE_TAP_MS = 400;
  const DOUBLE_TAP_PX = 30;
  /** the steps the arrows in the large pad move by, for touch screens without arrow keys */
  const NUDGE_STEPS = [0.005, 0.02, 0.05];

  const places = $derived(pagePlaces(page));
  const lines = $derived(Array.from({ length: Math.max(0, page.rows - 1) }, (_, i) => i + 1));
  /** the pointer dragging along a gradient or over a pad */
  let dragging: number | null = null;
  const placeOf = (cell: PartyCell) => ({ page: pageIndex, row: cell.row, column: cell.column });

  /** the last touch on a pad, to see a double tap */
  let lastTap: { row: number; column: number; time: number; x: number; y: number } | null = null;
  /** the pad shown large, by its place on the page */
  let zoomed: { row: number; column: number } | null = $state(null);
  let zoomOpen = $state(false);
  const zoomedCell = $derived(zoomed
    ? page.cells.find((c) => c.row === zoomed!.row && c.column === zoomed!.column && c.fader?.kind === 'pan-tilt')
      ?? null
    : null);
  const zoomedPoint = $derived(zoomedCell ? faderPoint(status, placeOf(zoomedCell)) : null);
  let nudgeStep = $state(NUDGE_STEPS[0]);

  /** One step of the large pad's arrows: dx, dy are -1, 0 or 1. */
  function nudge(cell: PartyCell, dx: number, dy: number) {
    const { x, y } = zoomedPoint ?? { x: 0.5, y: 0.5 };
    const clamp = (n: number) => Math.min(1, Math.max(0, Math.round(n * 1000) / 1000));
    onslide?.(cell, clamp(x + dx * nudgeStep), clamp(y + dy * nudgeStep));
  }

  function style(cell: PartyCell): string {
    return `--fill: ${cell.color}; --edge: ${cell.borderColor}; --ink: ${textColorFor(cell.color)}`;
  }

  function faderStyle(cell: PartyCell): string {
    return `${style(cell)}; --gradient: ${cell.fader ? faderBackground(cell.fader.kind) : 'none'}`;
  }

  function area(row: number, column: number, span: number, rowSpan: number): string {
    return `grid-row: ${gridRow(row, rowSpan)}; grid-column: ${column + 1} / span ${span}`;
  }

  function isSelected(row: number, column: number): boolean {
    return selected?.row === row && selected?.column === column;
  }

  function slideTo(event: PointerEvent, cell: PartyCell) {
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (cell.fader?.kind === 'pan-tilt') {
      const { x, y } = padPointAt(event.clientX, event.clientY, box);
      onslide?.(cell, x, y);
    } else {
      onslide?.(cell, pointAt(event.clientX, box.left, box.width), 0.5);
    }
  }

  /** A second touch on the same pad right after the first: true, and the pad opens large. */
  function isDoubleTap(event: PointerEvent, cell: PartyCell): boolean {
    if (cell.fader?.kind !== 'pan-tilt') return false;
    const previous = lastTap;
    lastTap = { row: cell.row, column: cell.column, time: event.timeStamp, x: event.clientX, y: event.clientY };
    if (!previous || previous.row !== cell.row || previous.column !== cell.column
      || event.timeStamp - previous.time > DOUBLE_TAP_MS
      || Math.hypot(event.clientX - previous.x, event.clientY - previous.y) > DOUBLE_TAP_PX) return false;
    lastTap = null;
    return true;
  }

  function startSlide(event: PointerEvent, cell: PartyCell) {
    event.preventDefault();
    if (isDoubleTap(event, cell)) {
      zoomed = { row: cell.row, column: cell.column };
      zoomOpen = true;
      return;
    }
    dragging = event.pointerId;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    slideTo(event, cell);
  }

  function moveSlide(event: PointerEvent, cell: PartyCell) {
    if (dragging === event.pointerId) slideTo(event, cell);
  }

  function endSlide(event: PointerEvent) {
    if (dragging === event.pointerId) dragging = null;
  }

  function keySlide(event: KeyboardEvent, cell: PartyCell, point: { x: number; y: number } | null) {
    const pad = cell.fader?.kind === 'pan-tilt';
    const { x, y } = point ?? (pad ? { x: 0.5, y: 0.5 } : { x: 0, y: 0.5 });
    const moves: Record<string, [number, number]> = pad
      ? { ArrowLeft: [-KEY_STEP, 0], ArrowRight: [KEY_STEP, 0], ArrowUp: [0, KEY_STEP], ArrowDown: [0, -KEY_STEP] }
      : { ArrowLeft: [-KEY_STEP, 0], ArrowDown: [-KEY_STEP, 0], ArrowRight: [KEY_STEP, 0], ArrowUp: [KEY_STEP, 0],
        Home: [-1, 0], End: [1, 0] };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    const clamp = (n: number) => Math.min(1, Math.max(0, Math.round(n * 100) / 100));
    onslide?.(cell, clamp(x + move[0]), clamp(y + move[1]));
  }
</script>

<div class="matrix" class:dense={page.columns >= DENSE_COLUMNS} class:fill
  style="--columns: {page.columns}; grid-template-rows: {rowTracks(page, editing, fill)}">
  {#each lines as row (row)}
    {#if editing}
      <button type="button" class="separator edit" class:on={page.separators.includes(row)} style="grid-row: {2 * row}"
        aria-pressed={page.separators.includes(row)}
        title={page.separators.includes(row) ? 'Remove the line' : 'Draw a line between these rows'}
        onclick={() => ontoggleseparator?.(row)}><span></span></button>
    {:else if page.separators.includes(row)}
      <div class="separator on" style="grid-row: {2 * row}" aria-hidden="true"><span></span></div>
    {/if}
  {/each}
  {#each places as { row, column, span, rowSpan, cell } (`${row}/${column}`)}
    {#if editing}
      <button type="button" class="cell" class:empty={!cell} class:text={cell && !playsSomething(cell)}
        class:fader={cell?.fader} class:selected={isSelected(row, column)}
        style="{cell ? (cell.fader ? faderStyle(cell) : style(cell)) : ''}; {area(row, column, span, rowSpan)}"
        aria-label="Field {row + 1}/{column + 1}" onclick={() => onselect?.(row, column)}>
        {#if cell}
          <span class="label">{cellLabel(cell, scenes, compositions, moves)}</span>
          <span class="meta">
            {#if span > 1 || rowSpan > 1}<span>{span} × {rowSpan}</span>{/if}
            {#if cell.mode === 'flash' && !cell.fader}<span>flash</span>{/if}
            {#if cell.group}<span>▣ {cell.group}</span>{/if}
          </span>
        {:else}
          <span class="plus">+</span>
        {/if}
      </button>
    {:else if cell?.fader}
      {@const on = isOn(status, placeOf(cell))}
      {@const point = faderPoint(status, placeOf(cell))}
      {@const label = cellLabel(cell, scenes, compositions, moves)}
      {@const pad = cell.fader.kind === 'pan-tilt'}
      <div class="cell fader" class:pad class:on role="slider" tabindex="0" aria-label={label}
        title={pad ? 'Double-tap to open it large' : undefined}
        aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round((point?.x ?? 0) * 100)}
        aria-valuetext={pad && point ? `Pan ${Math.round(point.x * 100)} %, tilt ${Math.round(point.y * 100)} %`
          : undefined}
        style="{faderStyle(cell)}; {area(row, column, span, rowSpan)}"
        onpointerdown={(e) => startSlide(e, cell)} onpointermove={(e) => moveSlide(e, cell)}
        onpointerup={endSlide} onpointercancel={endSlide} onkeydown={(e) => keySlide(e, cell, point)}>
        <span class="label">{label}</span>
        {#if pad}
          {@const at = point ?? { x: 0.5, y: 0.5 }}
          <span class="cross h" class:on style="top: {(1 - at.y) * 100}%" aria-hidden="true"></span>
          <span class="cross v" class:on style="left: {at.x * 100}%" aria-hidden="true"></span>
          <span class="dot" class:on style="left: {at.x * 100}%; top: {(1 - at.y) * 100}%" aria-hidden="true"></span>
        {:else if point !== null}
          <span class="marker" class:on style="left: {point.x * 100}%" aria-hidden="true"></span>
        {/if}
        <span class="meta">
          <span class="state">{on ? 'on' : 'off'}</span>
          {#if pad}
            <button type="button" class="off" title="Open it large" aria-label="Open {label} large"
              onpointerdown={(e) => e.stopPropagation()}
              onclick={() => { zoomed = { row: cell.row, column: cell.column }; zoomOpen = true; }}>⤢</button>
          {/if}
          {#if on}
            <button type="button" class="off" title="Switch off" aria-label="Switch {label} off"
              onpointerdown={(e) => e.stopPropagation()} onclick={() => onpress?.(cell)}>✕</button>
          {/if}
          {#if cell.group}<span>{cell.group}</span>{/if}
        </span>
      </div>
    {:else if cell && playsSomething(cell)}
      {@const on = isOn(status, placeOf(cell))}
      <button type="button" class="cell" class:on style="{style(cell)}; {area(row, column, span, rowSpan)}"
        aria-pressed={on} title={cell.mode === 'flash' ? 'Hold' : on ? 'Switch off' : 'Switch on'}
        onpointerdown={(e) => { e.preventDefault(); onpress?.(cell); }}
        onpointerup={() => onrelease?.(cell)} onpointerleave={() => onrelease?.(cell)}
        onpointercancel={() => onrelease?.(cell)}
        onclick={(e) => { if (e.detail === 0) { onpress?.(cell); onrelease?.(cell); } }}>
        <span class="label">{cellLabel(cell, scenes, compositions, moves)}</span>
        <span class="meta">
          <span class="state">{cell.mode === 'flash' ? 'flash' : on ? 'on' : 'off'}</span>
          {#if cell.group}<span>{cell.group}</span>{/if}
        </span>
      </button>
    {:else if cell}
      <div class="cell text" style="{style(cell)}; {area(row, column, span, rowSpan)}">
        <span class="label">{cellLabel(cell, scenes, compositions, moves)}</span>
      </div>
    {:else}
      <div class="cell blank" style={area(row, column, 1, 1)} aria-hidden="true"></div>
    {/if}
  {/each}
</div>

{#if zoomedCell}
  {@const label = cellLabel(zoomedCell, scenes, compositions, moves)}
  {@const on = isOn(status, placeOf(zoomedCell))}
  <Modal bind:open={zoomOpen} title={label} wide>
    <div class="zoom">
      <div class="zoom-pad">
        <XYPad label={label} x={zoomedPoint?.x ?? 0.5} y={zoomedPoint?.y ?? 0.5} digits={1}
          onchange={(x, y) => onslide?.(zoomedCell, x, y)} />
      </div>
      <div class="nudge" role="group" aria-label="Fine steps">
        <button class="btn" type="button" style="grid-area: up" aria-label="Tilt up"
          onclick={() => nudge(zoomedCell, 0, 1)}>▲</button>
        <button class="btn" type="button" style="grid-area: left" aria-label="Pan left"
          onclick={() => nudge(zoomedCell, -1, 0)}>◀</button>
        <button class="btn" type="button" style="grid-area: right" aria-label="Pan right"
          onclick={() => nudge(zoomedCell, 1, 0)}>▶</button>
        <button class="btn" type="button" style="grid-area: down" aria-label="Tilt down"
          onclick={() => nudge(zoomedCell, 0, -1)}>▼</button>
        <div class="steps" style="grid-area: step" role="radiogroup" aria-label="Step">
          {#each NUDGE_STEPS as step (step)}
            <button type="button" role="radio" aria-checked={nudgeStep === step} class:on={nudgeStep === step}
              onclick={() => (nudgeStep = step)}>{step * 100} %</button>
          {/each}
        </div>
      </div>
    </div>
    <p class="dim">Left to right is pan, bottom to top is tilt. The arrows move in steps of the size chosen below
      them; on a keyboard the arrow keys move in fine steps, with shift in larger ones.</p>
    {#snippet actions()}
      {#if on}
        <button class="btn" type="button" onclick={() => onpress?.(zoomedCell)}>Switch off</button>
      {/if}
      <button class="btn primary" type="button" onclick={() => (zoomOpen = false)}>Done</button>
    {/snippet}
  </Modal>
{/if}

<style>
  /* the large pad: as big as the dialog allows, still square and without scrolling; the arrows beside it on a wide
     screen (a tablet in landscape), below it on a narrow one */
  .zoom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 20px;
  }

  .zoom-pad {
    flex: 0 1 auto;
    width: min(100%, calc(100dvh - 280px));
    min-width: 200px;
  }

  .nudge {
    display: grid;
    grid-template-areas: '. up .' 'left . right' '. down .' 'step step step';
    grid-template-columns: repeat(3, 56px);
    grid-auto-rows: 56px;
    gap: 6px;
  }

  .nudge .btn {
    justify-content: center;
    padding: 0;
    font-size: 18px;
    touch-action: manipulation;
  }

  .steps {
    display: flex;
    align-self: center;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    overflow: hidden;
  }

  .steps button {
    flex: 1;
    height: 40px;
    border: 0;
    background: transparent;
    color: var(--text-dim);
    font: inherit;
    font-size: 12px;
    touch-action: manipulation;
    cursor: pointer;
  }

  .steps button.on {
    background: var(--accent-soft);
    color: var(--text);
    font-weight: 700;
  }

  /* one grid for the page: rows and the tracks between them alternate (see rowTracks) */
  .matrix {
    display: grid;
    grid-template-columns: repeat(var(--columns), minmax(0, 1fr));
    column-gap: 10px;
  }

  .matrix.fill {
    height: 100%;
  }

  .separator {
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    margin: 0;
    padding: 0;
    border: 0;
    background: transparent;
  }

  .separator span {
    flex: 1;
    height: 2px;
    border-radius: 1px;
    background: transparent;
  }

  .separator.on span {
    background: var(--border-strong);
    box-shadow: 0 0 8px var(--border-strong);
  }

  .separator.edit {
    cursor: pointer;
  }

  .separator.edit span {
    border-top: 1px dashed var(--border);
    height: 0;
  }

  .separator.edit.on span {
    border-top: 0;
    height: 3px;
    background: var(--accent);
  }

  .separator.edit:hover span,
  .separator.edit:focus-visible span {
    border-top-color: var(--accent);
  }

  .cell {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: 6px;
    min-height: 84px;
    padding: 10px 12px;
    border: 2px solid var(--edge, var(--border));
    border-radius: var(--radius-sm);
    background: var(--fill, transparent);
    color: var(--ink, var(--text));
    font: inherit;
    text-align: left;
    user-select: none;
    touch-action: manipulation;
    transition: box-shadow 0.12s, transform 0.06s, filter 0.12s;
  }

  button.cell {
    cursor: pointer;
  }

  /* off: a little dimmer, so “on” stands out */
  button.cell:not(.on):not(.empty) {
    filter: saturate(0.7) brightness(0.75);
  }

  .cell.on {
    box-shadow: 0 0 0 2px var(--edge), 0 0 26px var(--edge);
  }

  button.cell:active:not(.empty) {
    transform: scale(0.98);
  }

  .cell.text {
    justify-content: center;
    border-style: dashed;
    filter: none !important;
  }

  .cell.text .label {
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .cell.blank {
    border-color: transparent;
  }

  .cell.empty {
    align-items: center;
    justify-content: center;
    border: 1px dashed var(--border);
    color: var(--text-faint);
    filter: none;
  }

  .cell.selected {
    outline: 3px solid var(--accent);
    outline-offset: 2px;
  }

  .label {
    font-weight: 700;
    overflow-wrap: break-word;
    hyphens: auto;
  }

  .dense .cell {
    min-height: 72px;
    padding: 8px;
  }

  /* filling the window: the rows share the height, however small that makes them */
  .fill .cell {
    min-height: 0;
    overflow: hidden;
  }

  .dense .label {
    font-size: 13px;
  }

  .plus {
    font-size: 20px;
  }

  .meta {
    display: flex;
    gap: 8px;
    min-width: 0;
    max-width: 100%;
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    opacity: 0.8;
  }

  .meta > span {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .meta > .state {
    flex-shrink: 0;
  }

  .cell.on .state {
    font-weight: 800;
  }

  /* a gradient: the colors or intensities to touch, the point last touched as a marker */
  .cell.fader {
    background: var(--gradient);
    touch-action: none;
    cursor: pointer;
  }

  div.cell.fader:not(.on) {
    filter: saturate(0.7) brightness(0.75);
  }

  .cell.fader .label,
  .cell.fader .meta {
    position: relative;
    z-index: 1;
    width: fit-content;
    padding: 1px 6px;
    border-radius: 4px;
    background: rgb(0 0 0 / 55%);
    color: #e8ecf4;
    pointer-events: none;
  }

  .cell.fader .off {
    pointer-events: auto;
    padding: 0 4px;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    font-weight: 800;
    letter-spacing: inherit;
    text-transform: inherit;
    cursor: pointer;
  }

  /* a finger needs more room than the small switches in the corner */
  @media (pointer: coarse) {
    .cell.fader .off {
      padding: 4px 10px;
      font-size: 15px;
    }
  }

  .marker {
    position: absolute;
    top: 4px;
    bottom: 4px;
    width: 6px;
    margin-left: -3px;
    border: 2px solid #0b0d12;
    border-radius: 3px;
    background: #ffffff;
    opacity: 0.6;
    pointer-events: none;
  }

  .marker.on {
    opacity: 1;
    box-shadow: 0 0 10px #ffffff;
  }

  /* a pad: pan from left to right, tilt from bottom to top */
  .cell.pad {
    cursor: crosshair;
  }

  .cross {
    position: absolute;
    background: rgb(255 255 255 / 25%);
    pointer-events: none;
  }

  .cross.on {
    background: var(--edge);
  }

  .cross.h {
    left: 0;
    right: 0;
    height: 1px;
  }

  .cross.v {
    top: 0;
    bottom: 0;
    width: 1px;
  }

  .dot {
    position: absolute;
    width: 22px;
    height: 22px;
    border: 2px solid #0b0d12;
    border-radius: 50%;
    background: #ffffff;
    opacity: 0.6;
    transform: translate(-50%, -50%);
    pointer-events: none;
  }

  .dot.on {
    opacity: 1;
    background: var(--edge);
    box-shadow: 0 0 16px var(--edge), 0 0 4px #ffffff;
  }
</style>
