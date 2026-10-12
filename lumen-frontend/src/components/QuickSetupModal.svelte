<script lang="ts">
  import Modal from './Modal.svelte';
  import QuickSetupOptions from './QuickSetupOptions.svelte';
  import { api } from '../lib/api';
  import {
    abilitiesOf, ALL_OPTIONS, fitting, makesSomething, quickSetupRequest, resultSummary,
    type QuickSetupOptions as Options,
  } from '../lib/quickSetup';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture } from '../lib/types';

  let {
    open = $bindable(false),
    projectId,
    fixtures,
    preselected = [],
    pages = [],
  }: {
    open?: boolean;
    projectId: number;
    /** the fixtures to choose from */
    fixtures: Fixture[];
    preselected?: number[];
    /** the names of the party pages */
    pages?: string[];
  } = $props();

  let chosen = $state<number[]>([]);
  let name = $state('');
  let options = $state<Options>({ ...ALL_OPTIONS });
  let running = $state(false);

  // a fresh start every time the dialog opens
  $effect(() => {
    if (open) {
      chosen = [...preselected];
      name = '';
      options = { ...ALL_OPTIONS };
    }
  });

  const modeOf = (fixture: Fixture) => app.fixtureType(fixture.fixtureTypeId)?.modes.find((m) => m.name === fixture.mode);
  const abilities = $derived(abilitiesOf(fixtures.filter((f) => chosen.includes(f.id)).map(modeOf)));
  const effective = $derived(fitting(options, abilities, pages.length));

  function toggle(id: number) {
    chosen = chosen.includes(id) ? chosen.filter((c) => c !== id) : [...chosen, id];
  }

  async function run() {
    running = true;
    try {
      // in patch order, so the buttons and moves follow the fixtures
      const ids = fixtures.filter((f) => chosen.includes(f.id)).map((f) => f.id);
      const result = await api.quickSetup(projectId, quickSetupRequest(ids, name, effective));
      notify(resultSummary(result));
      result.notes.forEach((note) => notify(note));
      open = false;
    } catch (e) {
      showError(e);
    } finally {
      running = false;
    }
  }
</script>

<Modal bind:open title="Quick setup">
  <p class="dim intro">
    Makes looks, compositions, moves and party fields for the chosen fixtures together – one party button switches them
    all. Everything can be changed or deleted afterwards.
  </p>
  <fieldset class="list">
    <legend>Fixtures</legend>
    {#each fixtures as fixture (fixture.id)}
      <label class="check"><input type="checkbox" checked={chosen.includes(fixture.id)}
        onchange={() => toggle(fixture.id)} /> {fixture.name}</label>
    {:else}
      <p class="dim">Nothing patched yet.</p>
    {/each}
  </fieldset>
  <label class="field">Name<input bind:value={name} placeholder="From the fixture names" maxlength="60" /></label>
  <QuickSetupOptions bind:options {abilities} {pages} />
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (open = false)}>Cancel</button>
    <button class="btn primary" type="button" onclick={run}
      disabled={running || chosen.length === 0 || !makesSomething(effective)}>Set up</button>
  {/snippet}
</Modal>

<style>
  .intro {
    margin: 0 0 12px;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 220px;
    overflow-y: auto;
    margin: 0 0 12px;
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .list legend {
    padding: 0 4px;
    color: var(--text-dim);
    font-size: 12px;
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .field {
    margin-bottom: 12px;
  }
</style>
