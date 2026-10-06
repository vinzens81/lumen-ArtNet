<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import UpdateFromProgrammerModal from '../components/UpdateFromProgrammerModal.svelte';
  import { api } from '../lib/api';
  import { modesOf, previewOf, rgbToCss, swatchesOf } from '../lib/color';
  import { compositionsPlaying } from '../lib/compositions';
  import { focus } from '../lib/focus.svelte';
  import { shortTime } from '../lib/format';
  import { live } from '../lib/live.svelte';
  import { presetNames } from '../lib/presets';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Composition, Fixture, Look, LookRevision, Preset, RecordRequest } from '../lib/types';

  let looks = $state<Look[]>([]);
  let compositions = $state<Composition[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let presets = $state<Preset[]>([]);
  let revisions = $state<LookRevision[]>([]);
  let selectedId = $state<number | null>(null);
  let showOwn = $state(false);
  let name = $state('');
  let createOpen = $state(false);
  let createName = $state('');
  let updateOpen = $state(false);
  let deleteOpen = $state(false);

  const projectId = $derived(app.active?.id ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const compositionNames = $derived(new Map(compositions.map((c) => [c.id, c.name])));
  const shared = $derived(looks.filter((l) => l.owner === null));
  /** own looks of compositions, grouped by composition, steps in order */
  const own = $derived.by(() => {
    const groups = new Map<number, Look[]>();
    for (const composition of compositions) {
      const ids = composition.steps.map((s) => s.lookId);
      const list = looks.filter((l) => l.owner === composition.id)
        .sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
      if (list.length > 0) groups.set(composition.id, list);
    }
    return groups;
  });
  const selected = $derived(looks.find((l) => l.id === selectedId) ?? null);
  const playedBy = $derived(selected ? compositionsPlaying(selected.id, compositions) : []);
  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);

  $effect(() => {
    void live.looksRevision;
    void live.compositionsRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    void live.presetsRevision;
    if (projectId !== null) api.presets.list(projectId).then((list) => (presets = list), showError);
  });

  $effect(() => {
    void live.looksRevision;
    if (projectId !== null && selectedId !== null) loadRevisions(projectId, selectedId);
    else revisions = [];
  });

  async function load(id: number) {
    try {
      [looks, compositions, fixtures] = await Promise.all([
        api.looks.list(id),
        api.compositions.list(id),
        api.fixtures.list(id),
      ]);
      if (focus.lookId !== null && looks.some((l) => l.id === focus.lookId)) {
        select(looks.find((l) => l.id === focus.lookId)!);
        focus.lookId = null;
      } else if (selectedId !== null && !looks.some((l) => l.id === selectedId)) {
        selectedId = null;
      }
    } catch (e) {
      showError(e);
    }
  }

  async function loadRevisions(project: number, lookId: number) {
    try {
      const loaded = await api.looks.revisions(project, lookId);
      if (selectedId === lookId) revisions = loaded;
    } catch (e) {
      showError(e);
    }
  }

  function select(look: Look) {
    selectedId = look.id;
    name = look.name;
    if (look.owner !== null) showOwn = true;
  }

  async function create() {
    if (projectId === null) return;
    try {
      const look = await api.looks.create(projectId, createName || 'Look', programmerHasValues);
      createOpen = false;
      await load(projectId);
      select(look);
    } catch (e) {
      showError(e);
    }
  }

  async function rename() {
    if (projectId === null || !selected || !name.trim() || name === selected.name) return;
    try {
      await api.looks.update(projectId, selected.id, name);
    } catch (e) {
      showError(e);
    }
  }

  async function makeShared() {
    if (projectId === null || !selected) return;
    try {
      await api.looks.update(projectId, selected.id, selected.name, true);
      notify(`“${selected.name}” is now a shared look – every composition can play it`);
    } catch (e) {
      showError(e);
    }
  }

  async function record(request: RecordRequest) {
    if (projectId === null || !selected) return;
    try {
      await api.looks.record(projectId, selected.id, request);
      updateOpen = false;
      notify(`“${selected.name}” ${request.merge ? 'merged with' : 'updated from'} the programmer`);
    } catch (e) {
      showError(e);
    }
  }

  async function restore(revision: LookRevision) {
    if (projectId === null || !selected) return;
    try {
      await api.looks.restore(projectId, selected.id, revision.id);
      notify(`“${selected.name}”: content from ${shortTime(new Date(revision.createdAt))} restored`);
    } catch (e) {
      showError(e);
    }
  }

  async function loadIntoProgrammer() {
    if (projectId === null || !selected) return;
    try {
      await api.looks.load(projectId, selected.id);
      notify(`“${selected.name}” loaded into the programmer`);
    } catch (e) {
      showError(e);
    }
  }

  async function duplicate() {
    if (projectId === null || !selected) return;
    try {
      const copy = await api.looks.duplicate(projectId, selected.id);
      await load(projectId);
      select(copy);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (projectId === null || !selected) return;
    deleteOpen = false;
    try {
      await api.looks.remove(projectId, selected.id);
      selectedId = null;
    } catch (e) {
      showError(e);
    }
  }

  function openComposition(composition: Composition) {
    focus.compositionId = composition.id;
    navigate('/setup/compositions');
  }
</script>

{#snippet lookRow(look: Look, label: string)}
  <button type="button" class="item" class:active={look.id === selectedId} onclick={() => select(look)}>
    <span class="text">
      <span class="name">{label}</span>
      <span class="dim small">{look.fixtureCount} {look.fixtureCount === 1 ? 'fixture' : 'fixtures'}</span>
    </span>
    <span class="swatches" aria-hidden="true">
      {#each swatchesOf(fixtures, modes, look.values).slice(0, 8) as swatch, j (j)}
        <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
      {:else}
        <span class="dim small">dark</span>
      {/each}
    </span>
  </button>
{/snippet}

<section class="view">
  <header class="head">
    <div>
      <h1>Looks</h1>
      <p class="dim">
        Fixed values recorded from the programmer – the building blocks of compositions. A shared look can be played
        by several compositions: change it here and it changes everywhere.
      </p>
    </div>
    <button class="btn record" type="button" disabled={projectId === null}
      onclick={() => { createName = ''; createOpen = true; }}>
      {programmerHasValues ? '⏺ Record look' : '+ Empty look'}
    </button>
  </header>

  {#if !app.active}
    <div class="glass empty">Activate a project first.</div>
  {:else}
    <div class="layout">
      <nav class="glass list" aria-label="Looks">
        {#if shared.length === 0}
          <p class="dim small pad">No shared looks yet. Build one in the programmer and record it.</p>
        {/if}
        {#each shared as look (look.id)}{@render lookRow(look, look.name)}{/each}
        <label class="check own-toggle">
          <input type="checkbox" bind:checked={showOwn} /> Show the looks of compositions
        </label>
        {#if showOwn}
          {#each [...own] as [compositionId, list] (compositionId)}
            <h3 class="group">{compositionNames.get(compositionId)}</h3>
            {#each list as look (look.id)}{@render lookRow(look, look.name)}{/each}
          {/each}
        {/if}
      </nav>

      <article class="glass panel">
        {#if !selected}
          <p class="dim">Select a look.</p>
        {:else}
          <form class="name-row" onsubmit={(e) => { e.preventDefault(); rename(); }}>
            <label class="field grow">Name<input bind:value={name} onblur={rename} /></label>
            {#if selected.owner !== null}
              <button class="btn small" type="button" onclick={makeShared}
                title="Make it a shared look that any composition can play">◆ Make shared</button>
            {/if}
          </form>
          <p class="hint dim">
            {#if selected.owner !== null}
              Belongs to “{compositionNames.get(selected.owner)}” (a recorded or generated step).
            {:else if playedBy.length === 0}
              Shared look, played by no composition yet.
            {:else}
              Played by
              {#each playedBy as composition, i (composition.id)}{i > 0 ? ', ' : ''}<button type="button" class="link"
                onclick={() => openComposition(composition)}>{composition.name}</button>{/each}.
            {/if}
          </p>

          <div class="buttons">
            <button class="btn" type="button" onclick={loadIntoProgrammer}>Load into programmer</button>
            <button class="btn record-outline" type="button" onclick={() => (updateOpen = true)}
              disabled={!programmerHasValues}>⏺ Update from programmer</button>
            {#if revisions.length > 0}
              <button class="btn" type="button" onclick={() => restore(revisions[0])}
                title="Restore the content from before “{revisions[0].reason}” ({shortTime(new Date(revisions[0].createdAt))})">↶ Undo</button>
            {/if}
          </div>

          <ul class="fixtures">
            {#each fixtures.filter((f) => selected?.values[f.id]) as fixture (fixture.id)}
              {@const mode = modes.get(fixture.id)}
              {@const preview = mode ? previewOf(mode, selected.values[fixture.id]) : null}
              <li>
                <span class="swatch" style="--color: {preview ? rgbToCss(preview.color) : '#888'}; --intensity: {preview?.intensity ?? 0}"></span>
                {fixture.name}
                {#each presetNames(selected.presets, fixture.id, presets) as preset (preset)}
                  <span class="preset-tag" title="Takes its values from this preset">{preset}</span>
                {/each}
                <span class="mono dim">{Math.round((selected.values[fixture.id].dimmer ?? 0) * 100)}%</span>
              </li>
            {:else}
              <li class="dim">No fixtures – everything is dark in this look.</li>
            {/each}
          </ul>

          {#if revisions.length > 0}
            <h3>History</h3>
            <ul class="revisions">
              {#each revisions as revision (revision.id)}
                <li>
                  <span class="mono">{shortTime(new Date(revision.createdAt))}</span>
                  <span class="reason dim">before “{revision.reason}”</span>
                  <span class="swatches" aria-hidden="true">
                    {#each swatchesOf(fixtures, modes, revision.values).slice(0, 8) as swatch, j (j)}
                      <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
                    {:else}<span class="dim small">dark</span>{/each}
                  </span>
                  <button class="btn small" type="button" onclick={() => restore(revision)}>Restore</button>
                </li>
              {/each}
            </ul>
          {/if}

          <footer class="buttons">
            <button class="btn small ghost" type="button" onclick={duplicate} title="A shared copy">⧉ Duplicate</button>
            <button class="btn small ghost danger" type="button" onclick={() => (deleteOpen = true)}>Delete</button>
          </footer>
        {/if}
      </article>
    </div>
  {/if}
</section>

<Modal bind:open={createOpen} title={programmerHasValues ? 'Record look' : 'New empty look'}>
  <form id="look-form" onsubmit={(e) => { e.preventDefault(); create(); }}>
    <label class="field">Name<input bind:value={createName} placeholder="Look" /></label>
    <p class="dim small">
      {programmerHasValues ? 'Takes everything the programmer sets.' : 'The look starts dark – fill it later with “Update”.'}
    </p>
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (createOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="look-form">Create</button>
  {/snippet}
</Modal>

<UpdateFromProgrammerModal bind:open={updateOpen} subject="the look “{selected?.name}”"
  note={playedBy.length > 1 ? `It is played by ${playedBy.length} compositions – all of them change.` : undefined}
  onconfirm={record} />

<Modal bind:open={deleteOpen} title="Delete look">
  <p>Delete the look <strong>{selected?.name}</strong>? A look still played by a composition cannot be deleted.</p>
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
  }

  .head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .head p {
    margin: 6px 0 0;
    max-width: 640px;
  }

  .layout {
    display: grid;
    grid-template-columns: 300px minmax(0, 1fr);
    gap: 12px;
    align-items: start;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px;
    max-height: calc(100vh - 220px);
    overflow: auto;
  }

  .item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .item:hover {
    background: var(--panel-raised);
  }

  .item.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .group {
    margin: 10px 4px 2px;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .own-toggle {
    margin: 10px 4px 0;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }

  .pad {
    padding: 6px;
  }

  .panel {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 18px;
  }

  .name-row {
    display: flex;
    align-items: flex-end;
    gap: 10px;
  }

  .grow {
    flex: 1;
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .link {
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent);
    font: inherit;
    cursor: pointer;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .swatches {
    display: flex;
    gap: 2px;
  }

  .swatch {
    width: 10px;
    height: 16px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--color) calc(var(--intensity) * 100%), #111);
  }

  .fixtures,
  .revisions {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .fixtures li,
  .revisions li {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.85rem;
  }

  .reason {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .preset-tag {
    padding: 0 6px;
    border: 1px solid var(--border-strong);
    border-radius: 999px;
    color: var(--text-dim);
    font-size: 0.75rem;
  }

  h3 {
    margin: 4px 0 0;
    font-size: 13px;
  }

  .small {
    font-size: 12px;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
