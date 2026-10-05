<script lang="ts">
  import { ApiError, api } from '../lib/api';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { OflImportResult } from '../lib/types';
  import Modal from './Modal.svelte';

  let { open = $bindable(false) }: { open?: boolean } = $props();

  let manufacturer = $state('');
  let file = $state<File | null>(null);
  let replace = $state(false);
  let busy = $state(false);
  let conflict = $state(false);
  let result = $state<OflImportResult | null>(null);

  $effect(() => {
    if (open) {
      file = null;
      replace = false;
      conflict = false;
      result = null;
    }
  });

  async function upload() {
    if (!file) return;
    busy = true;
    try {
      const json = new Blob([await file.text()], { type: 'application/json' });
      result = await api.library.importOfl(json, manufacturer.trim(), replace);
      conflict = false;
      await app.reloadLibrary();
      notify(`Imported “${result.fixtureType.manufacturer} ${result.fixtureType.model}”`);
    } catch (e) {
      conflict = e instanceof ApiError && e.status === 409;
      showError(e);
    } finally {
      busy = false;
    }
  }
</script>

<Modal bind:open title="Import from the Open Fixture Library" wide>
  {#if result}
    <div class="result">
      <p>
        <strong>{result.fixtureType.manufacturer} {result.fixtureType.model}</strong> is in the library now
        ({result.fixtureType.modes.length} {result.fixtureType.modes.length === 1 ? 'mode' : 'modes'}), saved as
        <span class="mono">{result.source}</span>.
      </p>
      {#if result.warnings.length > 0}
        <p class="dim">Check these points before using the fixture on stage:</p>
        <ul>
          {#each result.warnings as warning, i (i)}<li>{warning}</li>{/each}
        </ul>
      {:else}
        <p class="dim">Everything was converted. Still worth a look at the channels before the show.</p>
      {/if}
    </div>
  {:else}
    <form id="ofl-form" onsubmit={(e) => { e.preventDefault(); upload(); }}>
      <p class="dim">
        Find the fixture on <a href="https://open-fixture-library.org" target="_blank" rel="noreferrer">open-fixture-library.org</a>,
        choose <em>Download → Open Fixture Library JSON</em> and pick the file here. Lumen turns it into a profile in its
        own library folder, where it can be edited like any other.
      </p>
      <label class="field">Manufacturer
        <input bind:value={manufacturer} placeholder="Cameo" required />
      </label>
      <label class="field">Fixture file (.json)
        <input type="file" accept=".json,application/json" required
          onchange={(e) => (file = (e.currentTarget as HTMLInputElement).files?.[0] ?? null)} />
      </label>
      {#if conflict}
        <label class="check">
          <input type="checkbox" bind:checked={replace} />
          Replace the existing profile with the same id
        </label>
      {/if}
    </form>
  {/if}
  {#snippet actions()}
    {#if result}
      <button class="btn primary" type="button" onclick={() => (open = false)}>Done</button>
    {:else}
      <button class="btn ghost" type="button" onclick={() => (open = false)}>Cancel</button>
      <button class="btn primary" type="submit" form="ofl-form" disabled={busy || !file}>Import</button>
    {/if}
  {/snippet}
</Modal>

<style>
  form,
  .result {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  p {
    margin: 0;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    font-size: 0.9rem;
  }

  a {
    color: var(--accent);
  }
</style>
