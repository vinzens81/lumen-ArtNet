<script lang="ts">
  import {
    passageOf, passages, scriptBlocks, speakerHue, stopOf, stops, type Script, type ScriptPart,
  } from '../lib/script';

  let {
    script,
    currentLine = null,
    section = null,
  }: {
    script: Script;
    /** the sentence the operator is at: its whole passage is highlighted, the ones before dimmed; null = a preview */
    currentLine?: number | null;
    /** the part of the scene on stage (0 = start, n = after the n-th light marker), to mark the active marker */
    section?: number | null;
  } = $props();

  const blocks = $derived(scriptBlocks(script));
  /**
   * sentences of the current passage, e.g. the whole speech of the current speaker; at a light marker without speech
   * none, everything before it is done
   */
  const current = $derived.by(() => {
    if (currentLine === null) return null;
    const list = stops(script);
    const stop = list[stopOf(list, currentLine, section ?? 0)];
    if (stop.marker) return { first: stop.line, last: stop.line - 1 };
    const all = passages(script);
    const index = passageOf(all, currentLine);
    return index < 0 ? null : all[index];
  });
</script>

{#snippet spoken(parts: ScriptPart[])}
  {#each parts as part, i (i)}
    {#if part.kind === 'line'}
      <span class="line" class:done={current !== null && part.index < current.first}
        class:current={current !== null && part.index >= current.first && part.index <= current.last}
        data-line={part.index}>{part.text}</span>{' '}
    {:else if part.kind === 'break'}
      <br />
    {/if}
  {/each}
{/snippet}

<!-- the script as the show displays it: speeches under their speaker, stage directions on lines of their own -->
<div class="script-text">
  {#each blocks as block, i (i)}
    {#if block.kind === 'speech'}
      <div class="speech" style="--speaker: {speakerHue(block.speaker)}">
        {#if !block.continued}<div class="speaker">{block.speaker}</div>{/if}
        {#if block.parts.length > 0}<div class="said">{@render spoken(block.parts)}</div>{/if}
      </div>
    {:else if block.kind === 'text'}
      <div class="said">{@render spoken(block.parts)}</div>
    {:else if block.kind === 'note'}
      <div class="direction">{block.text}</div>
    {:else if block.kind === 'marker'}
      <div class="marker" data-marker={block.section} class:active={section === block.section} class:passed={section !== null && block.section < (section ?? 0)}>
        <span class="marker-name">💡 {block.name}</span>
        {#if block.info}<span class="marker-info">{block.info}</span>{/if}
      </div>
    {:else}
      <span class="paragraph"></span>
    {/if}
  {/each}
</div>

<style>
  /* a light marker: the light of the scene changes here */
  .marker {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin: 12px 0 6px;
    padding: 2px 10px;
    border-top: 1px dashed var(--warning);
    color: var(--warning);
    font-size: 0.7em;
    font-weight: 700;
    letter-spacing: 0.06em;
  }

  .marker.active {
    border-top-style: solid;
    background: var(--warning-soft);
  }

  .marker.passed {
    opacity: 0.45;
  }

  .marker-info {
    font-weight: 400;
    letter-spacing: 0;
    color: var(--text-dim);
  }

  .script-text {
    line-height: 1.65;
  }

  .line {
    padding: 2px 4px;
    border-radius: 6px;
    box-decoration-break: clone;
    -webkit-box-decoration-break: clone;
    transition: background 0.15s, color 0.15s, opacity 0.15s;
  }

  .line.done {
    opacity: 0.35;
  }

  .line.current {
    background: var(--accent-soft);
    color: var(--text);
    box-shadow: 0 0 0 2px var(--accent);
  }

  /* stage directions belong to no speaker: a line of their own, not indented */
  .direction {
    width: fit-content;
    margin: 6px 0;
    padding: 1px 8px;
    border-left: 3px solid var(--direction);
    border-radius: 4px;
    background: var(--direction-soft);
    color: var(--direction);
    font-size: 0.8em;
    font-style: italic;
    white-space: pre-line;
  }

  /* a speech: the name marked in the speaker's own color, the text indented below it */
  .speech {
    margin: 10px 0 4px;
  }

  .speech + .speech,
  .direction + .speech {
    margin-top: 6px;
  }

  .speaker {
    display: inline-block;
    margin-bottom: 2px;
    padding: 0 8px;
    border-radius: 5px;
    background: hsl(var(--speaker) 70% 60% / 0.16);
    color: hsl(var(--speaker) 80% 72%);
    font-size: 0.7em;
    font-weight: 800;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .speech .said {
    margin-left: 1.4em;
    padding-left: 12px;
    border-left: 2px solid hsl(var(--speaker) 70% 60% / 0.45);
  }

  .paragraph {
    display: block;
    height: 0.8em;
  }
</style>
