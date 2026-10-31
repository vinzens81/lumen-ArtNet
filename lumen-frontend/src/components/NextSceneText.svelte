<script lang="ts">
  import ScriptText from './ScriptText.svelte';
  import { parseScript } from '../lib/script';
  import type { Scene } from '../lib/types';

  /**
   * Below the script of the scene on stage: a clear cut, then the script of the next scene to read ahead – dimmed and
   * without highlighting, it is not reached by stepping through the script.
   */
  let { next, end = false }: {
    /** the next scene, as chosen by the operator */
    next: Scene | undefined;
    /** the show ends with the next GO */
    end?: boolean;
  } = $props();

  const script = $derived(parseScript(next?.script ?? ''));
</script>

{#if next || end}
  <div class="next-scene">
    <div class="cut" role="separator">
      <span class="label">
        {#if next}GO ▸ <span class="mono">{next.number}</span> {next.name}{:else}GO ▸ End of the show{/if}
      </span>
    </div>
    {#if next?.cueNote}<p class="cue">{next.cueNote}</p>{/if}
    {#if next}
      {#if script.parts.length > 0}
        <div class="preview"><ScriptText {script} /></div>
      {:else}
        <p class="empty">This scene has no text.</p>
      {/if}
    {/if}
  </div>
{/if}

<style>
  .next-scene {
    margin-top: 2em;
  }

  /* a scene change, not a light marker: a thick bar in the color of GO across the whole width */
  .cut {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 0;
  }

  .cut::before,
  .cut::after {
    content: '';
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background: var(--accent);
    box-shadow: 0 0 12px var(--accent-glow);
  }

  .label {
    flex: none;
    max-width: 70%;
    padding: 6px 16px;
    border: 2px solid var(--accent);
    border-radius: 999px;
    background: var(--accent-soft);
    color: var(--accent);
    font-size: 0.9em;
    font-weight: 800;
    letter-spacing: 0.04em;
    text-align: center;
  }

  .cue {
    margin: 4px auto 14px;
    max-width: 80%;
    color: var(--warning);
    font-size: 0.8em;
    font-weight: 600;
    text-align: center;
  }

  /* what comes after: readable, but clearly not where the show is */
  .preview {
    opacity: 0.5;
  }

  .empty {
    margin: 0;
    color: var(--text-dim);
    font-size: 0.8em;
    text-align: center;
  }
</style>
