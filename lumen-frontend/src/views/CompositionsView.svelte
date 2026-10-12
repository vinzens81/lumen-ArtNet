<script lang="ts">
  import CompositionEditor from '../components/CompositionEditor.svelte';
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { modesOf } from '../lib/color';
  import FolderHead from '../components/FolderHead.svelte';
  import {
    byFolder, closedFolders, compositionIcon, effectLabel, folderNames, isFast, rememberClosedFolders, scenesUsing,
  } from '../lib/compositions';
  import { focus } from '../lib/focus.svelte';
  import { live } from '../lib/live.svelte';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Composition, Fixture, Group, Look, Scene } from '../lib/types';

  let compositions = $state<Composition[]>([]);
  let looks = $state<Look[]>([]);
  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedId = $state<number | null>(null);
  let createOpen = $state(false);
  let deleteOpen = $state(false);
  let form = $state({ name: '', record: false });
  let closed = $state<string[]>(closedFolders('compositions'));

  const projectId = $derived(app.active?.id ?? null);
  const selected = $derived(compositions.find((c) => c.id === selectedId) ?? null);
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);
  const folders = $derived(byFolder(compositions));
  const names = $derived(folderNames(compositions));

  function toggleFolder(folder: string) {
    closed = closed.includes(folder) ? closed.filter((f) => f !== folder) : [...closed, folder];
    rememberClosedFolders('compositions', closed);
  }

  $effect(() => {
    void live.compositionsRevision;
    void live.looksRevision;
    void live.scenesRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  async function load(id: number) {
    try {
      [compositions, looks, scenes, fixtures, groups] = await Promise.all([
        api.compositions.list(id),
        api.looks.list(id),
        api.scenes.list(id),
        api.fixtures.list(id),
        api.groups.list(id),
      ]);
      if (focus.compositionId !== null && compositions.some((c) => c.id === focus.compositionId)) {
        selectedId = focus.compositionId;
        focus.compositionId = null;
      }
      if (selectedId === null || !compositions.some((c) => c.id === selectedId)) {
        selectedId = compositions[0]?.id ?? null;
      }
    } catch (e) {
      showError(e);
    }
  }

  function openCreate() {
    form = { name: '', record: programmerHasValues };
    createOpen = true;
  }

  async function create() {
    if (projectId === null) return;
    try {
      let created = await api.compositions.create(projectId, { name: form.name || 'Composition' });
      if (form.record) created = await api.compositions.recordStep(projectId, created.id);
      createOpen = false;
      compositions = [...compositions, created].sort((a, b) => a.name.localeCompare(b.name));
      selectedId = created.id;
    } catch (e) {
      showError(e);
    }
  }

  async function duplicate() {
    if (projectId === null || !selected) return;
    try {
      const copy = await api.compositions.duplicate(projectId, selected.id);
      notify(`Copied to “${copy.name}”`);
      await load(projectId);
      selectedId = copy.id;
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (projectId === null || !selected) return;
    // the live reload after the delete can drop it from the list before the request returns
    const composition = selected;
    try {
      await api.compositions.remove(projectId, composition.id);
      notify(`Deleted “${composition.name}”`);
      deleteOpen = false;
      selectedId = null;
      await load(projectId);
    } catch (e) {
      deleteOpen = false;
      showError(e);
    }
  }

  function saved(composition: Composition) {
    compositions = compositions.map((c) => (c.id === composition.id ? composition : c));
  }

  function subtitle(composition: Composition): string {
    const steps = `${composition.steps.length} ${composition.steps.length === 1 ? 'step' : 'steps'}`;
    const source = composition.generator ? `${effectLabel(composition.generator.kind)} · ` : '';
    const used = scenesUsing(composition.id, scenes).length;
    return `${source}${steps}${used > 0 ? ` · ${used} ${used === 1 ? 'scene' : 'scenes'}` : ''}`;
  }
</script>

<section class="view">
  <header class="head">
    <h1>Compositions</h1>
    <button class="btn primary" type="button" onclick={openCreate} disabled={projectId === null}>+ New composition</button>
  </header>

  {#if !app.active}
    <div class="glass empty">Activate a project first.</div>
  {:else if compositions.length === 0}
    <div class="glass empty">
      <h2>No compositions yet</h2>
      <p>Record a look in the programmer as a first step, or generate a chase for a row of fixtures.</p>
      <button class="btn primary" type="button" onclick={openCreate}>+ New composition</button>
    </div>
  {:else}
    <div class="layout">
      <nav class="glass list" aria-label="Compositions">
        {#each folders as { folder, items: inFolder } (folder)}
          {@const open = !closed.includes(folder)}
          <FolderHead {folder} count={inFolder.length} {open} ontoggle={() => toggleFolder(folder)} />
          {#if open}
            {#each inFolder as composition (composition.id)}
              {@const onStage = stagePreview.status.compositionId === composition.id}
              <button type="button" class="item" class:active={composition.id === selectedId} class:on-stage={onStage}
                onclick={() => (selectedId = composition.id)}>
                <span class="icon" aria-hidden="true">{compositionIcon(composition)}</span>
                <span class="text">
                  <span class="name">{composition.name}</span>
                  <span class="dim small">{subtitle(composition)}</span>
                </span>
                {#if isFast(composition)}<span class="fast" title="Fast flashing">⚡</span>{/if}
                {#if onStage}
                  <span class="on-stage-tag" title="This composition is playing on stage (Show on stage)">
                    <span class="pulse" aria-hidden="true"></span>ON STAGE
                  </span>
                {/if}
              </button>
            {/each}
          {/if}
        {/each}
      </nav>

      <article class="glass panel">
        {#if selected && projectId !== null}
          <CompositionEditor {projectId} composition={selected} {looks} {scenes} {fixtures} {groups} {modes}
            folders={names} onSaved={saved} />
          <footer class="footer">
            <button class="btn small ghost" type="button" onclick={duplicate}
              title="A copy with copies of its own looks, in no scene">⧉ Duplicate</button>
            <button class="btn small ghost danger" type="button" onclick={() => (deleteOpen = true)}>Delete</button>
          </footer>
        {:else}
          <p class="dim">Select a composition.</p>
        {/if}
      </article>
    </div>
  {/if}

  <p class="dim help">
    Steps that play looks – a single look stays, several run through with fades. Scenes use compositions: a change
    shows in every scene. Chases, rainbows and other effects are generated into steps. Folders only sort the list:
    quick setup puts what it makes into "Auto generated", the folder of a composition is set next to its name.
  </p>
</section>

<Modal bind:open={createOpen} title="New composition">
  <form id="composition-form" onsubmit={(e) => { e.preventDefault(); create(); }}>
    <label class="field">Name<input bind:value={form.name} placeholder="Composition" /></label>
    <label class="check">
      <input type="checkbox" bind:checked={form.record} disabled={!programmerHasValues} />
      Record the programmer as the first step
      {#if !programmerHasValues}<span class="dim small">(the programmer is empty)</span>{/if}
    </label>
    <p class="dim small">Afterwards: record more steps, add shared looks, or generate steps from an effect.</p>
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (createOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="composition-form">Create</button>
  {/snippet}
</Modal>

<Modal bind:open={deleteOpen} title="Delete composition">
  <p>Delete <strong>{selected?.name}</strong> with its own looks?</p>
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
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .head h1 {
    margin: 0;
  }

  .help {
    margin: 0;
    max-width: 820px;
    font-size: 12px;
  }

  .layout {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr);
    gap: 12px;
    align-items: start;
  }

  /* the list scrolls on its own, the composition beside it stays */
  .list {
    position: sticky;
    top: 84px;
    max-height: calc(100vh - 200px);
    overflow-y: auto;
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

  /* playing on stage as a preview: glowing edge, independent of the selection */
  .item.on-stage {
    box-shadow: inset 3px 0 0 var(--accent), -4px 0 14px -6px var(--accent-glow);
  }

  .on-stage-tag {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 6px;
    border-radius: 6px;
    background: var(--accent);
    color: var(--accent-ink);
    font-size: 9px;
    font-weight: 800;
    letter-spacing: 0.1em;
    white-space: nowrap;
  }

  .on-stage-tag .pulse {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--accent-ink);
    animation: on-stage-pulse 1.2s ease-in-out infinite;
  }

  @keyframes on-stage-pulse {
    50% {
      opacity: 0.25;
    }
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

  .small {
    font-size: 12px;
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .list {
      position: static;
      max-height: 320px;
    }
  }
</style>
