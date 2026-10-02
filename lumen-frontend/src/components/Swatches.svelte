<script lang="ts">
  import type { Swatch } from '../lib/color';

  let { swatches, max = 24 }: { swatches: Swatch[]; max?: number } = $props();
</script>

<span class="swatches" aria-hidden="true">
  {#each swatches.slice(0, max) as swatch, i (i)}
    <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
  {/each}
  {#if swatches.length > max}<span class="more">+{swatches.length - max}</span>{/if}
</span>

<style>
  .swatches {
    display: flex;
    gap: 4px;
    max-width: 260px;
    flex-wrap: wrap;
    justify-content: flex-end;
    align-items: center;
  }

  .swatch {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--color) calc(var(--intensity) * 100%), #111);
    box-shadow: 0 0 calc(10px * var(--intensity)) var(--color);
    flex: none;
  }

  .more {
    font-size: 11px;
    color: var(--text-dim);
  }
</style>
