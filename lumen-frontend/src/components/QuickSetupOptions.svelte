<script lang="ts">
  import { makesPartyRows, type Abilities, type QuickSetupOptions } from '../lib/quickSetup';

  let {
    options = $bindable(),
    abilities,
    pages = [],
    disabled = false,
  }: {
    options: QuickSetupOptions;
    /** what the fixtures can do; the rest is offered but switched off */
    abilities: Abilities;
    /** the names of the pages of the party matrix */
    pages?: string[];
    disabled?: boolean;
  } = $props();

  const pageValue = $derived(options.page === null ? 'auto' : options.page === 'new' ? 'new'
    : options.page < pages.length ? String(options.page) : 'auto');

  function choosePage(value: string) {
    options.page = value === 'auto' ? null : value === 'new' ? 'new' : Number(value);
  }
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
    <span>Party gradients <span class="dim">touch to pick intensity{abilities.gradient ? ' and color' : ''}{abilities.moves
      ? ', a pan/tilt pad' : ''}</span></span></label>
  <label class="page" class:off={!makesPartyRows(options)}>Party page
    <select value={pageValue} disabled={disabled || !makesPartyRows(options)}
      onchange={(e) => choosePage(e.currentTarget.value)}>
      <option value="auto">First page with room</option>
      {#each pages as name, i (i)}<option value={String(i)}>{name}</option>{/each}
      <option value="new">New page</option>
    </select>
  </label>
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

  .check.off,
  .page.off {
    color: var(--text-faint);
  }

  .page {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    font-size: 13px;
  }

  .page select {
    flex: 1;
    min-width: 0;
  }

  .dim {
    font-size: 12px;
  }
</style>
