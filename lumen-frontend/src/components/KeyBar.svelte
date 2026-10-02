<script lang="ts">
  import { kindIcon } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import type { KeyStatus } from '../lib/types';

  let { keys }: { keys: KeyStatus[] } = $props();

  /** keys held down right now (pointer or keyboard), so a flash key is released exactly once */
  const held = new Set<number>();

  function press(key: KeyStatus) {
    if (!key.available || held.has(key.slot)) return;
    held.add(key.slot);
    live.keyCommand(key.slot, 'press');
  }

  function release(key: KeyStatus) {
    if (!held.delete(key.slot)) return;
    if (key.mode === 'flash') live.keyCommand(key.slot, 'release');
  }

  function byDigit(event: KeyboardEvent): KeyStatus | undefined {
    if (!/^[1-8]$/.test(event.key)) return undefined;
    return keys.find((k) => k.slot === Number(event.key));
  }

  function onkeydown(event: KeyboardEvent) {
    if ((event.target as HTMLElement).closest('input, textarea, select') || event.metaKey || event.ctrlKey) return;
    const key = byDigit(event);
    if (!key) return;
    event.preventDefault();
    if (!event.repeat) press(key);
  }

  function onkeyup(event: KeyboardEvent) {
    const key = byDigit(event);
    if (key) release(key);
  }
</script>

<svelte:window {onkeydown} {onkeyup} />

{#if keys.length > 0}
  <div class="keys" role="group" aria-label="Extra keys">
    {#each keys as key (key.slot)}
      <button type="button" class="key" class:on={key.on} class:fading={key.level > 0 && !key.on}
        disabled={!key.available} style="--level: {key.level}"
        title={key.available
          ? `${key.name} – ${key.mode === 'flash' ? 'hold' : 'press to switch on and off'} (key ${key.slot})`
          : `${key.name} – not available in this scene`}
        onpointerdown={(e) => { e.preventDefault(); press(key); }}
        onpointerup={() => release(key)} onpointerleave={() => release(key)} onpointercancel={() => release(key)}
        onclick={(e) => { if (e.detail === 0) { press(key); release(key); } }}>
        <span class="slot mono">{key.slot}</span>
        <span class="name">{kindIcon(key.kind)} {key.name}</span>
        <span class="mode">{key.mode === 'flash' ? 'flash' : key.on ? 'on' : 'off'}</span>
      </button>
    {/each}
  </div>
{/if}

<style>
  .keys {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }

  .key {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    min-height: 58px;
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-strong);
    background: color-mix(in srgb, var(--accent) calc(var(--level) * 40%), var(--panel-raised));
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: manipulation;
    user-select: none;
    box-shadow: 0 0 calc(24px * var(--level)) var(--accent-glow);
    transition: background 0.1s, box-shadow 0.1s;
  }

  .key.on {
    border-color: var(--accent);
  }

  .key:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }

  .slot {
    position: absolute;
    top: 6px;
    right: 8px;
    font-size: 11px;
    color: var(--text-faint);
  }

  .name {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 600;
    font-size: 13px;
  }

  .mode {
    font-size: 10px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .key.on .mode {
    color: var(--accent);
  }
</style>
