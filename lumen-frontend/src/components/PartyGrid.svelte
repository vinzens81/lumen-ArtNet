<script lang="ts">
  import { cellLabel, isOn, matrixGrid, playsSomething, textColorFor } from '../lib/party';
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
  } = $props();

  const grid = $derived(matrixGrid(matrix));

  function style(cell: PartyCell): string {
    return `--fill: ${cell.color}; --edge: ${cell.borderColor}; --ink: ${textColorFor(cell.color)}`;
  }

  function isSelected(row: number, column: number): boolean {
    return selected?.row === row && selected?.column === column;
  }
</script>

<div class="matrix" style="--columns: {matrix.columns}">
  {#each grid as cells, row (row)}
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
      {#each cells as cell, column (column)}
        {#if editing}
          <button type="button" class="cell" class:empty={!cell} class:text={cell && !playsSomething(cell)}
            class:selected={isSelected(row, column)} style={cell ? style(cell) : ''}
            aria-label="Field {row + 1}/{column + 1}" onclick={() => onselect?.(row, column)}>
            {#if cell}
              <span class="label">{cellLabel(cell, scenes, compositions, moves)}</span>
              <span class="meta">
                {#if cell.mode === 'flash'}<span>flash</span>{/if}
                {#if cell.group}<span>▣ {cell.group}</span>{/if}
              </span>
            {:else}
              <span class="plus">+</span>
            {/if}
          </button>
        {:else if cell && playsSomething(cell)}
          {@const on = isOn(status, row, column)}
          <button type="button" class="cell" class:on style={style(cell)} aria-pressed={on}
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
          <div class="cell text" style={style(cell)}><span class="label">{cellLabel(cell, scenes, compositions, moves)}</span></div>
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
    overflow-wrap: anywhere;
  }

  .plus {
    font-size: 20px;
  }

  .meta {
    display: flex;
    gap: 8px;
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    opacity: 0.8;
  }

  .cell.on .state {
    font-weight: 800;
  }
</style>
