<script lang="ts">
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { positionedFixtures } from '../lib/moves';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Scene } from '../lib/types';
  import Modal from './Modal.svelte';

  let {
    open = $bindable(false),
    suggestedNumber = '',
    onrecorded,
  }: { open?: boolean; suggestedNumber?: string; onrecorded?: (scene: Scene) => void } = $props();

  let form = $state({ number: '', name: '', cueNote: '', position: true });

  /** heads with pan and tilt in the programmer: their position becomes a move of the scene */
  const heads = $derived(positionedFixtures(live.programmer, Object.keys(live.programmer).map(Number)));

  $effect(() => {
    if (open) form = { number: suggestedNumber, name: '', cueNote: '', position: true };
  });

  async function record() {
    const project = app.active;
    if (!project) return;
    try {
      const scene = await api.scenes.create(project.id, {
        number: form.number.trim() || null,
        name: form.name,
        cueNote: form.cueNote || null,
        autoFollowMs: null,
        record: true,
      });
      let result = scene;
      if (form.position && heads.length > 0) {
        // look + position: the position of the heads becomes a move of the scene
        const move = await api.moves.recordPosition(project.id, null, `${scene.name} – position`, heads);
        result = await api.scenes.setSection(project.id, scene.id,
          { marker: null, compositions: scene.compositions, looks: scene.looks, moves: [{ moveId: move.id }] });
      }
      notify(`Recorded scene ${scene.number} "${scene.name}"`);
      open = false;
      onrecorded?.(result);
    } catch (e) {
      showError(e);
    }
  }
</script>

<Modal bind:open title="Record scene">
  <form id="record-scene-form" onsubmit={(e) => { e.preventDefault(); record(); }}>
    <p class="dim">
      Saves the current programmer values as a new scene. Fixtures without values are dark in it. Pan and tilt are not
      part of the look – they become a move.
    </p>
    <div class="row">
      <label class="field number">Number<input bind:value={form.number} placeholder="next" inputmode="decimal" /></label>
      <label class="field">Name<input bind:value={form.name} placeholder="Dawn" required /></label>
    </div>
    <label class="field">Cue note<input bind:value={form.cueNote} placeholder="GO on: “…and then the sun rose”" /></label>
    {#if heads.length > 0}
      <label class="check">
        <input type="checkbox" bind:checked={form.position} />
        Also record where the {heads.length} {heads.length === 1 ? 'head points' : 'heads point'} as a move of the scene
      </label>
    {/if}
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (open = false)}>Cancel</button>
    <button class="btn record" type="submit" form="record-scene-form">⏺ Record</button>
  {/snippet}
</Modal>

<style>
  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  p {
    margin: 0;
  }

  .number {
    flex: 0 0 110px;
  }
</style>
