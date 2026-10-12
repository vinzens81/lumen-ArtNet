<script lang="ts">
  import { fixtureHue, occupancy, UNIVERSE_SIZE } from '../lib/patch';
  import type { Fixture } from '../lib/types';

  let {
    fixtures,
    selectedIds = [],
    preview = null,
    onSelectFixture,
    onSelectAddress,
  }: {
    fixtures: Fixture[];
    selectedIds?: number[];
    /** Channels a fixture being added would occupy. */
    preview?: { address: number; count: number } | null;
    /** additive: shift (or cmd/ctrl) held – add the fixture to the selection or take it out */
    onSelectFixture: (id: number, additive: boolean) => void;
    onSelectAddress: (address: number) => void;
  } = $props();

  const cells = $derived(occupancy(fixtures));
  const byId = $derived(new Map(fixtures.map((f) => [f.id, f])));

  function inPreview(channel: number): boolean {
    return preview !== null && channel >= preview.address && channel < preview.address + preview.count;
  }

  function cellLabel(channel: number, ids: number[]): string {
    if (ids.length === 0) return `Channel ${channel} – free`;
    return `Channel ${channel} – ${ids.map((id) => byId.get(id)?.name ?? id).join(', ')}`;
  }

  function cellStyle(ids: number[]): string {
    if (ids.length !== 1) return '';
    return `--hue: ${fixtureHue(ids[0])}`;
  }

  function isStart(channel: number, ids: number[]): boolean {
    return ids.some((id) => byId.get(id)?.address === channel);
  }
</script>

<div class="grid" role="grid" aria-label="DMX address grid">
  {#each { length: UNIVERSE_SIZE } as _, index (index)}
    {@const channel = index + 1}
    {@const ids = cells[index]}
    <button
      type="button"
      class="cell"
      class:used={ids.length === 1}
      class:overlap={ids.length > 1}
      class:selected={ids.some((id) => selectedIds.includes(id))}
      class:start={isStart(channel, ids)}
      class:preview={inPreview(channel)}
      class:preview-conflict={inPreview(channel) && ids.length > 0}
      style={cellStyle(ids)}
      title={cellLabel(channel, ids)}
      aria-label={cellLabel(channel, ids)}
      onclick={(e) => (ids.length > 0 ? onSelectFixture(ids[0], e.shiftKey || e.metaKey || e.ctrlKey)
        : onSelectAddress(channel))}
    >
      {#if isStart(channel, ids) || channel % 32 === 1}
        <span class="mono">{channel}</span>
      {/if}
    </button>
  {/each}
</div>

<style>
  .grid {
    display: grid;
    grid-template-columns: repeat(32, minmax(0, 1fr));
    gap: 3px;
    user-select: none;
  }

  .cell {
    position: relative;
    aspect-ratio: 1;
    min-width: 0;
    padding: 0;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.025);
    color: var(--text-faint);
    font-size: 8px;
    cursor: pointer;
    transition: transform 0.08s, box-shadow 0.15s;
  }

  .cell span {
    position: absolute;
    top: 1px;
    left: 2px;
    line-height: 1;
    pointer-events: none;
  }

  @media (max-width: 800px) {
    .grid {
      gap: 2px;
    }

    .cell span {
      display: none;
    }
  }

  .cell:hover {
    border-color: var(--accent);
    transform: scale(1.15);
    z-index: 1;
  }

  .cell.used {
    background: hsl(var(--hue) 85% 58% / 0.35);
    border-color: hsl(var(--hue) 85% 62% / 0.6);
    color: hsl(var(--hue) 90% 88%);
  }

  .cell.used.start {
    background: hsl(var(--hue) 85% 58% / 0.6);
  }

  .cell.selected {
    box-shadow: 0 0 10px hsl(var(--hue, 190) 90% 60% / 0.9);
    border-color: #fff;
  }

  .cell.overlap {
    background: repeating-linear-gradient(45deg, var(--danger) 0 3px, rgba(255, 77, 109, 0.35) 3px 6px);
    border-color: var(--danger);
    color: #fff;
  }

  .cell.preview {
    border-color: var(--accent);
    box-shadow: inset 0 0 0 1px var(--accent), 0 0 8px var(--accent-glow);
  }

  .cell.preview-conflict {
    box-shadow: inset 0 0 0 2px var(--danger), 0 0 10px var(--danger);
  }
</style>
