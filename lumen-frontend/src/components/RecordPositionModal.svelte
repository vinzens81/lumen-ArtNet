<script lang="ts">
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { positionedFixtures } from '../lib/moves';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Move } from '../lib/types';
  import Modal from './Modal.svelte';

  /**
   * "Record position": the pan/tilt of the selected fixtures in the programmer becomes a move with one point – their
   * middle – and each fixture keeps its distance from it as its own shift. A new move, or an existing one updated.
   */
  let { open = $bindable(false), fixtureIds }: { open?: boolean; fixtureIds: number[] } = $props();

  let moves = $state<Move[]>([]);
  let form = $state({ target: '', name: '' });

  const positioned = $derived(positionedFixtures(live.programmer, fixtureIds));

  $effect(() => {
    if (!open) return;
    form = { target: '', name: '' };
    const project = app.active;
    if (project) api.moves.list(project.id).then((list) => (moves = list)).catch(showError);
  });

  async function record() {
    const project = app.active;
    if (!project) return;
    try {
      const moveId = form.target ? Number(form.target) : null;
      const move = await api.moves.recordPosition(project.id, moveId, form.name || 'Position', positioned);
      notify(`${moveId ? 'Updated' : 'Recorded'} position “${move.name}” for ${move.fixtures.length} ${
        move.fixtures.length === 1 ? 'fixture' : 'fixtures'}`);
      open = false;
    } catch (e) {
      showError(e);
    }
  }
</script>

<Modal bind:open title="Record position">
  <form id="record-position-form" onsubmit={(e) => { e.preventDefault(); record(); }}>
    <p class="dim">
      Where the {positioned.length} selected {positioned.length === 1 ? 'head points' : 'heads point'} now becomes a
      move with one point. Scenes use it next to a look, so every color goes with every position.
    </p>
    <label class="field">Move
      <select bind:value={form.target}>
        <option value="">A new move</option>
        {#each moves as move (move.id)}<option value={String(move.id)}>Update “{move.name}”</option>{/each}
      </select>
    </label>
    {#if form.target === ''}
      <label class="field">Name<input bind:value={form.name} placeholder="Singer centre" required /></label>
    {/if}
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (open = false)}>Cancel</button>
    <button class="btn record" type="submit" form="record-position-form" disabled={positioned.length === 0}>
      ⏺ Record position</button>
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
</style>
