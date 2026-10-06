<script lang="ts">
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { FaderSetup, Scene } from '../lib/types';

  const MAX_SLOTS = 8;

  let { projectId, scenes }: { projectId: number; scenes: Scene[] } = $props();

  let faders = $state<FaderSetup[]>([]);

  const freeSlot = $derived(
    Array.from({ length: MAX_SLOTS }, (_, i) => i + 1).find((slot) => !faders.some((f) => f.slot === slot)) ?? null,
  );
  /** scenes outside the cue list first: they are made for this */
  const choices = $derived([...scenes].sort((a, b) => Number(b.outsideCueList) - Number(a.outsideCueList)));

  $effect(() => {
    void live.fadersRevision;
    load(projectId);
  });

  async function load(id: number) {
    try {
      faders = await api.faders.list(id);
    } catch (e) {
      showError(e);
    }
  }

  async function save(fader: FaderSetup) {
    try {
      await api.faders.save(projectId, fader.slot, fader.name?.trim() || null, fader.sceneId);
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function add() {
    const scene = choices[0];
    if (freeSlot === null || !scene) return;
    await save({ slot: freeSlot, name: null, sceneId: scene.id });
    notify(`Fader ${freeSlot} added – it appears in show mode`);
  }

  async function remove(slot: number) {
    try {
      await api.faders.remove(projectId, slot);
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }
</script>

<section class="faders">
  <h3>Faders</h3>
  <p class="hint dim">
    A fader plays a scene at the level it is pushed to, over the cue list – e.g. the house lights or a work light.
    It sits under GO in show mode. Usually the scene is one “only for extra keys”.
  </p>
  {#each faders as fader (fader.slot)}
    <div class="row">
      <span class="slot mono">{fader.slot}</span>
      <input class="name" value={fader.name ?? ''} placeholder="Scene name" aria-label="Name of fader {fader.slot}"
        onchange={(e) => save({ ...fader, name: (e.currentTarget as HTMLInputElement).value })} />
      <select value={fader.sceneId} aria-label="Scene of fader {fader.slot}"
        onchange={(e) => save({ ...fader, sceneId: Number((e.currentTarget as HTMLSelectElement).value) })}>
        {#each choices as scene (scene.id)}
          <option value={scene.id}>{scene.number} {scene.name}{scene.outsideCueList ? ' (not in cue list)' : ''}</option>
        {/each}
      </select>
      <button type="button" class="btn small ghost" onclick={() => remove(fader.slot)}
        aria-label="Remove fader {fader.slot}">✕</button>
    </div>
  {/each}
  <button type="button" class="btn small" onclick={add} disabled={freeSlot === null || scenes.length === 0}>
    + Fader
  </button>
</section>

<style>
  .faders {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px;
  }

  h3,
  p {
    margin: 0;
  }

  .hint {
    font-size: 0.8rem;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .slot {
    flex: 0 0 18px;
    color: var(--text-dim);
  }

  .name {
    flex: 1;
    min-width: 0;
  }

  select {
    flex: 1.4;
    min-width: 0;
  }

  .faders > .btn {
    align-self: flex-start;
  }
</style>
