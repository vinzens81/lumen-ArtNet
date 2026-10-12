<script lang="ts">
  import {
    cellLabel, faderBackground, faderPoint, isOn, matrixRows, playsSomething, pointAt, textColorFor,
  } from '../lib/party';
  import type { Composition, Move, PartyCell, PartyMatrix, PartyStatus, Scene } from '../lib/types';

  let {
    matrix,
    scenes,
    compositions,
    moves = new Map(),
    editing = false,
    status = null,
    selected = null,
    onselect,
    ontoggleseparator,
    onpress,
    onrelease,
    onslide,
  }: {
    matrix: PartyMatrix;
    scenes: Map<number, Scene>;
    compositions: Map<number, Composition>;
    moves?: Map<number, Move>;
    /** setup: every place can be selected, lines between rows can be switched */
    editing?: boolean;
    /** party mode: which fields are on */
    status?: PartyStatus | null;
    selected?: { row: number; column: number } | null;
    onselect?: (row: number, column: number) => void;
    ontoggleseparator?: (row: number) => void;
    onpress?: (cell: PartyCell) => void;
    onrelease?: (cell: PartyCell) => void;
    /** party mode: a gradient field touched at a point (0–1) */
    onslide?: (cell: PartyCell, point: number) => void;
  } = $props();

  /** keyboard steps on a gradient */
  const KEY_STEP = 0.05;
  /** from this many columns on, fields get smaller text */
  const DENSE_COLUMNS = 9;

  const rows = $derived(matrixRows(matrix));
  /** the pointer dragging along a gradient */
  let dragging: number | null = null;

  function style(cell: PartyCell): string {
    return `--fill: ${cell.color}; --edge: ${cell.borderColor}; --ink: ${textColorFor(cell.color)}`;
  }

  function faderStyle(cell: PartyCell): string {
    return `${style(cell)}; --gradient: ${cell.fader ? faderBackground(cell.fader.kind) : 'none'}`;
  }

  function isSelected(row: number, column: number): boolean {
    return selected?.row === row && selected?.column === column;
  }

  function slideTo(event: PointerEvent, cell: PartyCell) {
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    onslide?.(cell, pointAt(event.clientX, box.left, box.width));
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

  function keySlide(event: KeyboardEvent, cell: PartyCell, point: number | null) {
    const current = point ?? 0;
    const next = event.key === 'ArrowRight' || event.key === 'ArrowUp' ? current + KEY_STEP
      : event.key === 'ArrowLeft' || event.key === 'ArrowDown' ? current - KEY_STEP
        : event.key === 'Home' ? 0 : event.key === 'End' ? 1 : null;
    if (next === null) return;
    event.preventDefault();
    onslide?.(cell, Math.min(1, Math.max(0, Math.round(next * 100) / 100)));
  }
</script>

<div class="matrix" class:dense={matrix.columns >= DENSE_COLUMNS} style="--columns: {matrix.columns}">
  {#each rows as places, row (row)}
    {#if row > 0}
      {#if editing}
        <button type="button" class="separator edit" class:on={matrix.separators.includes(row)}
          aria-pressed={matrix.separators.includes(row)}
          title={matrix.separators.includes(row) ? 'Remove the line' : 'Draw a line between these rows'}
          onclick={() => ontoggleseparator?.(row)}><span></span></button>
      {:else if matrix.separators.includes(row)}
        <div class="separator on" aria-hidden="true"><span></span></div>
      {:else}
        <div class="gap" aria-hidden="true"></div>
      {/if}
    {/if}
    <div class="row">
      {#each places as { column, span, cell } (column)}
        {#if editing}
          <button type="button" class="cell" class:empty={!cell} class:text={cell && !playsSomething(cell)}
            class:fader={cell?.fader} class:selected={isSelected(row, column)}
            style="{cell ? (cell.fader ? faderStyle(cell) : style(cell)) : ''}; grid-column: span {span}"
            aria-label="Field {row + 1}/{column + 1}" onclick={() => onselect?.(row, column)}>
            {#if cell}
              <span class="label">{cellLabel(cell, scenes, compositions, moves)}</span>
              <span class="meta">
                {#if span > 1}<span>{span} columns</span>{/if}
                {#if cell.mode === 'flash' && !cell.fader}<span>flash</span>{/if}
                {#if cell.group}<span>▣ {cell.group}</span>{/if}
              </span>
            {:else}
              <span class="plus">+</span>
            {/if}
          </button>
        {:else if cell?.fader}
          {@const on = isOn(status, row, column)}
          {@const point = faderPoint(status, row, column)}
          {@const label = cellLabel(cell, scenes, compositions, moves)}
          <div class="cell fader" class:on role="slider" tabindex="0" aria-label={label}
            aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round((point ?? 0) * 100)}
            style="{faderStyle(cell)}; grid-column: span {span}"
            onpointerdown={(e) => startSlide(e, cell)} onpointermove={(e) => moveSlide(e, cell)}
            onpointerup={endSlide} onpointercancel={endSlide} onkeydown={(e) => keySlide(e, cell, point)}>
            <span class="label">{label}</span>
            {#if point !== null}<span class="marker" class:on style="left: {point * 100}%" aria-hidden="true"></span>{/if}
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
          {@const on = isOn(status, row, column)}
          <button type="button" class="cell" class:on style="{style(cell)}; grid-column: span {span}" aria-pressed={on}
            title={cell.mode === 'flash' ? 'Hold' : on ? 'Switch off' : 'Switch on'}
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
          <div class="cell text" style="{style(cell)}; grid-column: span {span}"><span class="label">{cellLabel(cell, scenes, compositions, moves)}</span></div>
        {:else}
          <div class="cell blank" aria-hidden="true"></div>
        {/if}
      {/each}
    </div>
  {/each}
</div>

<style>
  .matrix {
    display: flex;
    flex-direction: column;
  }

  .row {
    display: grid;
    grid-template-columns: repeat(var(--columns), minmax(0, 1fr));
    gap: 10px;
  }

  .gap {
    height: 10px;
  }

  .separator {
    display: flex;
    align-items: center;
    height: 22px;
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
</style>
