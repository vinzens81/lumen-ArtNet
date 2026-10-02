<script lang="ts">
  import { api } from '../lib/api';
  import { isFast, kindIcon, kindLabel, overlaps } from '../lib/compositions';
  import { live } from '../lib/live.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Composition, Fixture, Group, Mode } from '../lib/types';
  import CompositionEditor from './CompositionEditor.svelte';
  import Modal from './Modal.svelte';

  let {
    projectId,
    sceneId,
    fixtures,
    groups,
    modes,
  }: { projectId: number; sceneId: number; fixtures: Fixture[]; groups: Group[]; modes: Map<number, Mode> } = $props();

  let copies = $state<Composition[]>([]);
  let library = $state<Composition[]>([]);
  let insertId = $state('');
  let editingId = $state<number | null>(null);

  const editing = $derived(copies.find((c) => c.id === editingId) ?? null);
  const libraryNames = $derived(new Map(library.map((c) => [c.id, c.name])));
  const warnings = $derived(overlaps(copies, groups, fixtures));

  $effect(() => {
    void live.compositionsRevision;
    load(projectId, sceneId);
  });

  async function load(project: number, scene: number) {
    try {
      [copies, library] = await Promise.all([api.compositions.ofScene(project, scene), api.compositions.library(project)]);
    } catch (e) {
      showError(e);
    }
  }

  async function insert() {
    const id = Number(insertId);
    insertId = '';
    if (!id) return;
    try {
      const copy = await api.compositions.insert(projectId, sceneId, id);
      copies = [...copies, copy];
    } catch (e) {
      showError(e);
    }
  }

  async function move(index: number, step: number) {
    const ids = copies.map((c) => c.id);
    const other = index + step;
    if (other < 0 || other >= ids.length) return;
    [ids[index], ids[other]] = [ids[other], ids[index]];
    try {
      copies = await api.compositions.reorder(projectId, sceneId, ids);
    } catch (e) {
      showError(e);
    }
  }

  async function remove(copy: Composition) {
    try {
      await api.compositions.remove(projectId, copy.id);
      copies = copies.filter((c) => c.id !== copy.id);
    } catch (e) {
      showError(e);
    }
  }

  async function reloadFromLibrary(copy: Composition) {
    try {
      const updated = await api.compositions.reloadFromLibrary(projectId, copy.id);
      copies = copies.map((c) => (c.id === updated.id ? updated : c));
      notify(`"${updated.name}" reloaded from the library`);
    } catch (e) {
      showError(e);
    }
  }

  async function saveToLibrary(copy: Composition) {
    try {
      const updated = await api.compositions.saveToLibrary(projectId, copy.id);
      notify(`"${updated.name}" saved to the library – other scenes keep their copies`);
    } catch (e) {
      showError(e);
    }
  }

  function saved(updated: Composition) {
    copies = copies.map((c) => (c.id === updated.id ? updated : c));
  }
</script>

<section class="compositions">
  <h3>Compositions</h3>
  <p class="hint dim">Run over the look of this scene. Later ones win where they set the same thing.</p>

  {#if copies.length > 0}
    <ol class="list">
      {#each copies as copy, i (copy.id)}
        <li>
          <span class="icon" aria-hidden="true">{kindIcon(copy.kind)}</span>
          <button type="button" class="link" onclick={() => (editingId = copy.id)} title="Edit in this scene">
            <span class="name">{copy.name}</span>
            <span class="dim small">
              {kindLabel(copy.kind)} · {Math.round(copy.level * 100)} %{copy.speedFactor !== 1 ? ` · ${copy.speedFactor}×` : ''}
              {copy.restart ? ' · restarts' : ''}
              {copy.sourceId === null ? ' · not in library' : ''}
            </span>
          </button>
          {#if isFast(copy)}<span class="fast" title="Fast flashing">⚡</span>{/if}
          <span class="actions">
            <button class="btn small ghost" type="button" onclick={() => move(i, -1)} aria-label="Move up">↑</button>
            <button class="btn small ghost" type="button" onclick={() => move(i, 1)} aria-label="Move down">↓</button>
            <button class="btn small ghost danger" type="button" onclick={() => remove(copy)} aria-label="Remove from scene">✕</button>
          </span>
        </li>
      {/each}
    </ol>
  {/if}
  {#each warnings as warning, i (i)}<p class="hint warning-text">{warning}</p>{/each}

  <label class="field">
    <span class="sr-only">Insert composition</span>
    <select bind:value={insertId} onchange={insert} disabled={library.length === 0}>
      <option value="">{library.length === 0 ? 'Create compositions in the Compositions tab' : '+ Insert from library…'}</option>
      {#each library as composition (composition.id)}
        <option value={String(composition.id)}>{kindIcon(composition.kind)} {composition.name}</option>
      {/each}
    </select>
  </label>
</section>

<Modal wide bind:open={() => editing !== null, (open) => { if (!open) editingId = null; }} title="Composition in this scene">
  {#if editing}
    <CompositionEditor {projectId} composition={editing} {fixtures} {groups} {modes} onSaved={saved} />
    <p class="hint dim">
      Changes apply to this scene only.
      {#if editing.sourceId !== null}From library: <strong>{libraryNames.get(editing.sourceId) ?? '?'}</strong>{/if}
    </p>
  {/if}
  {#snippet actions()}
    {#if editing}
      {#if editing.sourceId !== null}
        <button class="btn ghost" type="button" onclick={() => editing && reloadFromLibrary(editing)}>Reload from library</button>
      {/if}
      <button class="btn ghost" type="button" onclick={() => editing && saveToLibrary(editing)}>Save to library</button>
    {/if}
    <button class="btn primary" type="button" onclick={() => (editingId = null)}>Done</button>
  {/snippet}
</Modal>

<style>
  .compositions {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .list li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    background: var(--panel-raised);
  }

  .icon {
    width: 20px;
    text-align: center;
  }

  .link {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    padding: 0;
    border: 0;
    background: none;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .link:hover .name {
    color: var(--accent);
  }

  .name {
    font-weight: 600;
  }

  .actions {
    display: flex;
  }

  .fast {
    color: var(--warning);
  }

  .hint {
    margin: 0;
    font-size: 12px;
  }

  .warning-text {
    color: var(--warning);
  }

  .small {
    font-size: 11px;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
