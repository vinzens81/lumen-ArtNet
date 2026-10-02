<script lang="ts">
  import { api } from '../lib/api';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Scene } from '../lib/types';
  import Modal from './Modal.svelte';

  let {
    open = $bindable(false),
    suggestedNumber = '',
    onrecorded,
  }: { open?: boolean; suggestedNumber?: string; onrecorded?: (scene: Scene) => void } = $props();

  let form = $state({ number: '', name: '', cueNote: '' });

  $effect(() => {
    if (open) form = { number: suggestedNumber, name: '', cueNote: '' };
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
      notify(`Recorded scene ${scene.number} "${scene.name}"`);
      open = false;
      onrecorded?.(scene);
    } catch (e) {
      showError(e);
    }
  }
</script>

<Modal bind:open title="Record scene">
  <form id="record-scene-form" onsubmit={(e) => { e.preventDefault(); record(); }}>
    <p class="dim">Saves the current programmer values as a new scene. Fixtures without values are dark in it.</p>
    <div class="row">
      <label class="field number">Number<input bind:value={form.number} placeholder="next" inputmode="decimal" /></label>
      <label class="field">Name<input bind:value={form.name} placeholder="Dawn" required /></label>
    </div>
    <label class="field">Cue note<input bind:value={form.cueNote} placeholder="GO on: “…and then the sun rose”" /></label>
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
