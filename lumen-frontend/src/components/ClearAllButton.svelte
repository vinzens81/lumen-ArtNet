<script lang="ts">
  import { live } from '../lib/live.svelte';

  /** fixtures that have programmer values */
  const ids = $derived(Object.keys(live.programmer).map(Number));
</script>

<button class="btn clear-all" type="button" onclick={() => live.clear(ids)} disabled={ids.length === 0}
  title={ids.length === 0 ? 'The programmer is empty' : `Reset the programmer values of ${ids.length} ${ids.length === 1 ? 'fixture' : 'fixtures'}`}>
  ✕ Clear all{#if ids.length > 0}<span class="count mono">{ids.length}</span>{/if}
</button>

<style>
  .clear-all {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .clear-all:not(:disabled) {
    border-color: var(--warning);
    color: var(--warning);
  }

  .count {
    padding: 0 6px;
    border-radius: 999px;
    background: var(--warning-soft);
    font-size: 11px;
  }
</style>
