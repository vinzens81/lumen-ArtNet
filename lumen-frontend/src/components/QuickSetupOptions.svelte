<script lang="ts">
  import type { Abilities, QuickSetupOptions } from '../lib/quickSetup';

  let {
    options = $bindable(),
    abilities,
    disabled = false,
  }: {
    options: QuickSetupOptions;
    /** what the fixtures can do; the rest is offered but switched off */
    abilities: Abilities;
    disabled?: boolean;
  } = $props();
</script>

<div class="options">
  <label class="check"><input type="checkbox" bind:checked={options.intensity} {disabled} />
    <span>Intensity looks <span class="dim">0 / 50 / 100 %</span></span></label>
  <label class="check" class:off={!abilities.colors}>
    <input type="checkbox" checked={options.colors && abilities.colors} disabled={disabled || !abilities.colors}
      onchange={(e) => (options.colors = e.currentTarget.checked)} />
    <span>Color looks <span class="dim">{abilities.colors ? 'red, orange, green, blue, violet + palette'
      : 'needs color mixing or a color wheel'}</span></span>
  </label>
  <label class="check" class:off={!abilities.moves}>
    <input type="checkbox" checked={options.moves && abilities.moves} disabled={disabled || !abilities.moves}
      onchange={(e) => (options.moves = e.currentTarget.checked)} />
    <span>Moves <span class="dim">{abilities.moves ? 'line, circle, eight' : 'needs pan and tilt'}</span></span>
  </label>
  <label class="check"><input type="checkbox" bind:checked={options.partyButtons} {disabled} />
    <span>Party row <span class="dim">a button for each look and move</span></span></label>
  <label class="check"><input type="checkbox" bind:checked={options.gradients} {disabled} />
    <span>Party gradients <span class="dim">touch to pick intensity{abilities.gradient ? ' and color' : ''}</span></span></label>
</div>

<style>
  .options {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .check {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 13px;
  }

  .check.off {
    color: var(--text-faint);
  }

  .dim {
    font-size: 12px;
  }
</style>
