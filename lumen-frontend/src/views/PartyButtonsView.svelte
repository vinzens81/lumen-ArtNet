<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, KeyMode, PartyButton } from '../lib/types';

  let buttons = $state<PartyButton[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let newName = $state('');
  let newMode = $state<KeyMode>('toggle');
  let deleting = $state<PartyButton | null>(null);
  let deleteOpen = $state(false);

  const projectId = $derived(app.active?.id ?? null);
  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);
  const fixtureNames = $derived(new Map(fixtures.map((f) => [f.id, f.shortName ?? f.name])));

  $effect(() => {
    void live.partyButtonsRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [buttons, fixtures] = await Promise.all([api.partyButtons.list(id), api.fixtures.list(id)]);
    } catch (e) {
      showError(e);
    }
  }

  async function record() {
    if (projectId === null || !newName.trim()) return;
    try {
      const button = await api.partyButtons.record(projectId, newName.trim(), newMode);
      notify(`Recorded party button “${button.name}”`);
      newName = '';
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function save(button: PartyButton, name: string, mode: KeyMode) {
    if (projectId === null || !name.trim()) return;
    try {
      await api.partyButtons.update(projectId, button.id, name.trim(), mode);
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function rerecord(button: PartyButton) {
    if (projectId === null) return;
    try {
      await api.partyButtons.rerecord(projectId, button.id);
      notify(`“${button.name}” updated from the programmer`);
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  async function loadIntoProgrammer(button: PartyButton) {
    if (projectId === null) return;
    try {
      await api.partyButtons.load(projectId, button.id);
      notify(`“${button.name}” loaded into the programmer`);
    } catch (e) {
      showError(e);
    }
  }

  async function move(index: number, by: number) {
    if (projectId === null) return;
    const ids = buttons.map((b) => b.id);
    const [id] = ids.splice(index, 1);
    ids.splice(index + by, 0, id);
    try {
      buttons = await api.partyButtons.reorder(projectId, ids);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (projectId === null || !deleting) return;
    try {
      await api.partyButtons.delete(projectId, deleting.id);
      deleteOpen = false;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  /** What the button sets, e.g. "Spot 1, Spot 2: gobo, prism". */
  function summary(button: PartyButton): string {
    const attributes = new Set<string>();
    Object.values(button.values).forEach((values) => Object.keys(values).forEach((a) => attributes.add(a)));
    const names = Object.keys(button.values).map((id) => fixtureNames.get(Number(id)) ?? `#${id}`);
    const shown = names.length > 3 ? `${names.slice(0, 3).join(', ')} +${names.length - 3}` : names.join(', ');
    return `${shown}: ${[...attributes].map((a) => a.replace(/^generic:/, '')).join(', ')}`;
  }
</script>

{#if !app.active}
  <div class="glass empty"><h2>No active project</h2></div>
{:else}
  <section class="view">
    <header class="head">
      <div>
        <h1>Party buttons <span class="badge experimental">Experimental</span></h1>
        <p class="dim">
          Own buttons for party mode – a gobo, a prism, strobe or any other channel. Set the values in the programmer,
          then record them here. Toggle switches on and off, Flash is on while held. Keys 1–9 play the first nine.
        </p>
      </div>
    </header>

    <form class="record glass" onsubmit={(e) => { e.preventDefault(); record(); }}>
      <label class="field">Name<input bind:value={newName} placeholder="e.g. Gobo stars" required /></label>
      <label class="field">Mode
        <select bind:value={newMode}>
          <option value="toggle">Toggle</option>
          <option value="flash">Flash (while held)</option>
        </select>
      </label>
      <button class="btn record-btn" type="submit" disabled={!programmerHasValues || !newName.trim()}
        title={programmerHasValues ? 'Take the current programmer values' : 'Set some values in the programmer first'}>
        ⏺ Record from programmer
      </button>
      {#if !programmerHasValues}
        <button class="btn ghost" type="button" onclick={() => navigate('/setup/programmer')}>Open programmer</button>
      {/if}
    </form>

    <div class="list glass">
      {#each buttons as button, i (button.id)}
        <div class="row">
          <span class="key mono">{i < 9 ? i + 1 : ''}</span>
          <input class="name" value={button.name} aria-label="Name of {button.name}"
            onchange={(e) => save(button, e.currentTarget.value, button.mode)} />
          <select value={button.mode} aria-label="Mode of {button.name}"
            onchange={(e) => save(button, button.name, e.currentTarget.value as KeyMode)}>
            <option value="toggle">Toggle</option>
            <option value="flash">Flash</option>
          </select>
          <span class="summary dim small" title={summary(button)}>{summary(button)}</span>
          <div class="actions">
            <button class="btn small" type="button" disabled={i === 0} aria-label="Move {button.name} up"
              onclick={() => move(i, -1)}>↑</button>
            <button class="btn small" type="button" disabled={i === buttons.length - 1}
              aria-label="Move {button.name} down" onclick={() => move(i, 1)}>↓</button>
            <button class="btn small" type="button" onclick={() => loadIntoProgrammer(button)}>Load</button>
            <button class="btn small" type="button" disabled={!programmerHasValues}
              onclick={() => rerecord(button)}>⏺ Update</button>
            <button class="btn small danger" type="button" onclick={() => { deleting = button; deleteOpen = true; }}>Delete</button>
          </div>
        </div>
      {:else}
        <p class="dim empty-list">No party buttons yet.</p>
      {/each}
    </div>
  </section>
{/if}

<Modal bind:open={deleteOpen} title="Delete party button?">
  <p>“{deleting?.name}” will be removed from party mode.</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (deleteOpen = false)}>Cancel</button>
    <button class="btn danger" type="button" onclick={remove}>Delete</button>
  {/snippet}
</Modal>

<style>
  .view {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 12px 8px;
    max-width: 1100px;
  }

  .empty {
    padding: 40px;
    text-align: center;
  }

  h1 {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0 0 4px;
  }

  .experimental {
    color: var(--warning);
    border-color: var(--warning);
  }

  .head p {
    margin: 0;
    max-width: 760px;
  }

  .record {
    display: flex;
    align-items: flex-end;
    gap: 12px;
    padding: 14px 16px;
    flex-wrap: wrap;
  }

  .record .field:first-child {
    flex: 1;
    min-width: 200px;
  }

  .record-btn {
    color: var(--record);
    border-color: var(--record);
  }

  .list {
    display: flex;
    flex-direction: column;
    padding: 6px 0;
  }

  .row {
    display: grid;
    grid-template-columns: 24px minmax(140px, 220px) 110px 1fr auto;
    align-items: center;
    gap: 12px;
    padding: 8px 16px;
    border-bottom: 1px solid var(--border);
  }

  .row:last-child {
    border-bottom: none;
  }

  .key {
    color: var(--text-dim);
    text-align: center;
  }

  .row input,
  .row select {
    min-height: 32px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .summary {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .actions {
    display: flex;
    gap: 6px;
  }

  .empty-list {
    padding: 16px;
    margin: 0;
  }
</style>
