<script lang="ts">
  import { live } from '../lib/live.svelte';
  import type { FaderStatus } from '../lib/types';
  import Fader from './Fader.svelte';

  let { faders }: { faders: FaderStatus[] } = $props();
</script>

{#if faders.length > 0}
  <div class="faders" role="group" aria-label="Faders">
    {#each faders as fader (fader.slot)}
      <div class="fader">
        <Fader label={fader.name} value={fader.level} color="var(--warning)"
          onchange={(level) => live.setFader(fader.slot, level)} />
        <div class="quick">
          <button type="button" class="btn small ghost" onclick={() => live.setFader(fader.slot, 0)}
            aria-label="{fader.name} off">0</button>
          <button type="button" class="btn small ghost" onclick={() => live.setFader(fader.slot, 1)}
            aria-label="{fader.name} full">Full</button>
        </div>
      </div>
    {/each}
  </div>
{/if}

<style>
  .faders {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 8px 14px;
  }

  .fader {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .quick {
    display: flex;
    gap: 4px;
  }
</style>
