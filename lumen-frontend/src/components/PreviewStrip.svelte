<script lang="ts">
  import { previewOf, rgbToCss } from '../lib/color';
  import type { AttributeValues, Fixture, Mode } from '../lib/types';

  let {
    fixtureIds,
    values,
    modes,
    fixtures,
  }: {
    fixtureIds: number[];
    values: Record<number, AttributeValues> | undefined;
    modes: Map<number, Mode>;
    fixtures: Fixture[];
  } = $props();

  const byId = $derived(new Map(fixtures.map((f) => [f.id, f])));

  function glow(id: number): string {
    const mode = modes.get(id);
    if (!mode) return '--glow: #444; --intensity: 0';
    const p = previewOf(mode, values?.[id] ?? {});
    return `--glow: ${rgbToCss(p.color)}; --intensity: ${p.intensity.toFixed(3)}`;
  }
</script>

<div class="strip" aria-label="Preview">
  {#each fixtureIds as id (id)}
    <span class="lamp" style={glow(id)} title={byId.get(id)?.name ?? `#${id}`}>
      <span class="label">{byId.get(id)?.shortName ?? byId.get(id)?.name ?? id}</span>
    </span>
  {:else}
    <span class="dim small">No fixtures in the target.</span>
  {/each}
</div>

<style>
  .strip {
    display: flex;
    flex-wrap: wrap;
    gap: 14px 10px;
    padding: 16px 14px 10px;
    border-radius: var(--radius-sm);
    background: #06070a;
    border: 1px solid var(--border);
    min-height: 64px;
    align-items: flex-start;
  }

  .lamp {
    position: relative;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    border: 1px solid var(--border-strong);
    background: color-mix(in srgb, var(--glow) calc(var(--intensity) * 100%), #111);
    box-shadow:
      0 0 calc(18px * var(--intensity)) var(--glow),
      0 0 calc(40px * var(--intensity)) color-mix(in srgb, var(--glow) 40%, transparent);
  }

  .label {
    position: absolute;
    top: 30px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 9px;
    color: var(--text-faint);
    white-space: nowrap;
    max-width: 48px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .small {
    font-size: 12px;
  }
</style>
