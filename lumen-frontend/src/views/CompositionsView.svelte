<script lang="ts">
  import CompositionEditor from '../components/CompositionEditor.svelte';
  import ExtraKeysPanel from '../components/ExtraKeysPanel.svelte';
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { modesOf } from '../lib/color';
  import { defaultParams, isFast, KINDS, kindIcon, kindLabel } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Composition, CompositionKind, Fixture, Group } from '../lib/types';

  let library = $state<Composition[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedId = $state<number | null>(null);
  let createOpen = $state(false);
  let deleteOpen = $state(false);
  let form = $state<{ name: string; kind: CompositionKind; groupId: string }>({ name: '', kind: 'chase', groupId: '' });

  const projectId = $derived(app.active?.id ?? null);
  const selected = $derived(library.find((c) => c.id === selectedId) ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));

  $effect(() => {
    void live.compositionsRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [library, fixtures, groups] = await Promise.all([
        api.compositions.library(id),
        api.fixtures.list(id),
        api.groups.list(id),
      ]);
      if (selectedId === null || !library.some((c) => c.id === selectedId)) selectedId = library[0]?.id ?? null;
    } catch (e) {
      showError(e);
    }
  }

  function openCreate() {
    form = { name: '', kind: 'chase', groupId: groups[0] ? String(groups[0].id) : '' };
    createOpen = true;
  }

  async function create() {
    if (projectId === null) return;
    const kind = form.kind;
    try {
      const created = await api.compositions.create(projectId, {
        name: form.name || kindLabel(kind),
        kind,
        params: defaultParams(kind),
        target: kind === 'steps' ? null : form.groupId ? { groupId: Number(form.groupId) } : { fixtureIds: [] },
        steps: kind === 'steps' ? [] : null,
      });
      createOpen = false;
      library = [...library, created].sort((a, b) => a.name.localeCompare(b.name));
      selectedId = created.id;
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (projectId === null || !selected) return;
    try {
      await api.compositions.remove(projectId, selected.id);
      notify(`Deleted "${selected.name}" – copies in scenes stay`);
      deleteOpen = false;
      selectedId = null;
      await load(projectId);
    } catch (e) {
      showError(e);
    }
  }

  function saved(composition: Composition) {
    library = library.map((c) => (c.id === composition.id ? composition : c));
  }
</script>

<section class="view">
  <header class="head">
    <div>
      <h1>Compositions</h1>
      <p class="dim">
        Chases, color cycles and other running effects. Insert them into scenes in the Scenes tab – each scene gets
        its own copy.
      </p>
    </div>
    <button class="btn primary" type="button" onclick={openCreate} disabled={projectId === null}>+ New composition</button>
  </header>

  {#if !app.active}
    <div class="glass empty">Activate a project first.</div>
  {:else if library.length === 0}
    <div class="glass empty">
      <h2>No compositions yet</h2>
      <p>Create a chase for a row of fixtures or a color cycle for your RGB PARs.</p>
      <button class="btn primary" type="button" onclick={openCreate}>+ New composition</button>
    </div>
  {:else}
    <div class="layout">
      <nav class="glass list" aria-label="Library">
        {#each library as composition (composition.id)}
          <button type="button" class="item" class:active={composition.id === selectedId}
            onclick={() => (selectedId = composition.id)}>
            <span class="icon" aria-hidden="true">{kindIcon(composition.kind)}</span>
            <span class="text">
              <span class="name">{composition.name}</span>
              <span class="dim small">{kindLabel(composition.kind)}</span>
            </span>
            {#if isFast(composition)}<span class="fast" title="Fast flashing">⚡</span>{/if}
          </button>
        {/each}
      </nav>

      <article class="glass panel">
        {#if selected && projectId !== null}
          <CompositionEditor {projectId} composition={selected} {fixtures} {groups} {modes} onSaved={saved} />
          <footer class="footer">
            <button class="btn small ghost danger" type="button" onclick={() => (deleteOpen = true)}>Delete</button>
          </footer>
        {:else}
          <p class="dim">Select a composition.</p>
        {/if}
      </article>
    </div>
    {#if projectId !== null}
      <ExtraKeysPanel {projectId} {library} {fixtures} {groups} {modes} />
    {/if}
  {/if}
</section>

<Modal bind:open={createOpen} title="New composition">
  <form id="composition-form" onsubmit={(e) => { e.preventDefault(); create(); }}>
    <div class="kinds" role="radiogroup" aria-label="Kind">
      {#each KINDS as kind (kind.kind)}
        <label class="kind" class:active={form.kind === kind.kind}>
          <input type="radio" name="kind" value={kind.kind} bind:group={form.kind} />
          <span class="kind-icon" aria-hidden="true">{kind.icon}</span>
          <span><strong>{kind.label}</strong><span class="dim small block">{kind.description}</span></span>
        </label>
      {/each}
    </div>
    <label class="field">Name<input bind:value={form.name} placeholder={kindLabel(form.kind)} /></label>
    {#if form.kind !== 'steps'}
      <label class="field">Runs on
        <select bind:value={form.groupId}>
          {#each groups as group (group.id)}<option value={String(group.id)}>Group: {group.name}</option>{/each}
          <option value="">Fixtures – choose afterwards</option>
        </select>
      </label>
    {/if}
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (createOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="composition-form">Create</button>
  {/snippet}
</Modal>

<Modal bind:open={deleteOpen} title="Delete composition">
  <p>Delete <strong>{selected?.name}</strong> from the library? Copies in scenes keep running.</p>
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
    grid-template-columns: 260px minmax(0, 1fr);
    gap: 12px;
    align-items: start;
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px;
  }

  .item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
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

  .icon {
    width: 24px;
    text-align: center;
    font-size: 18px;
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

  .fast {
    color: var(--warning);
  }

  .panel {
    padding: 18px;
  }

  .footer {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .kinds {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .kind {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    cursor: pointer;
  }

  .kind.active {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .kind input {
    position: absolute;
    opacity: 0;
    pointer-events: none;
  }

  .kind-icon {
    font-size: 20px;
  }

  .small {
    font-size: 12px;
  }

  .block {
    display: block;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
