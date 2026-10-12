<script lang="ts">
  import {
    cellLabel, faderBackground, faderPoint, gridRow, isOn, padPointAt, pagePlaces, playsSomething, pointAt, rowTracks,
    textColorFor,
  } from '../lib/party';
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

  const places = $derived(pagePlaces(page));
  const lines = $derived(Array.from({ length: Math.max(0, page.rows - 1) }, (_, i) => i + 1));
  /** the pointer dragging along a gradient or over a pad */
  let dragging: number | null = null;

  const placeOf = (cell: PartyCell) => ({ page: pageIndex, row: cell.row, column: cell.column });

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

  function startSlide(event: PointerEvent, cell: PartyCell) {
    event.preventDefault();
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

<style>
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
