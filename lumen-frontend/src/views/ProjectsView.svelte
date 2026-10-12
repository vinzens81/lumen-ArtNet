<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import { api } from '../lib/api';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import { curveLabel, FADE_CURVES } from '../lib/sceneTiming';
  import type { FadeCurve, Project } from '../lib/types';

  let editing = $state<Project | null>(null);
  let formOpen = $state(false);
  let form = $state({ name: '', venue: '', fadeIn: 3, fadeOut: 3, fadeCurve: 'linear' as FadeCurve });
  let deleting = $state<Project | null>(null);
  let deleteOpen = $state(false);

  function openCreate() {
    editing = null;
    form = { name: '', venue: '', fadeIn: 3, fadeOut: 3, fadeCurve: 'linear' };
    formOpen = true;
  }

  function openEdit(project: Project) {
    editing = project;
    form = {
      name: project.name,
      venue: project.venue ?? '',
      fadeIn: project.fadeInMs / 1000,
      fadeOut: project.fadeOutMs / 1000,
      fadeCurve: project.fadeCurve,
    };
    formOpen = true;
  }

  async function save() {
    const request = {
      name: form.name,
      venue: form.venue || null,
      fadeInMs: Math.round(form.fadeIn * 1000),
      fadeOutMs: Math.round(form.fadeOut * 1000),
      fadeCurve: form.fadeCurve,
      // the times for moving in the dark are set in the Scenes tab
      dimOutMs: editing?.dimOutMs ?? null,
      travelMs: editing?.travelMs ?? null,
    };
    try {
      if (editing) {
        await api.projects.update(editing.id, request);
        notify(`Saved "${request.name}"`);
      } else {
        const created = await api.projects.create(request);
        notify(`Created "${created.name}"`);
      }
      formOpen = false;
      await app.reloadProjects();
    } catch (e) {
      showError(e);
    }
  }

  async function confirmDelete() {
    if (!deleting) return;
    try {
      await api.projects.remove(deleting.id);
      notify(`Deleted "${deleting.name}"`);
      deleteOpen = false;
      await app.reloadProjects();
    } catch (e) {
      showError(e);
    }
  }

  let importInput: HTMLInputElement | undefined = $state();

  async function importFile(event: Event) {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    try {
      let content: unknown;
      try {
        content = JSON.parse(await file.text());
      } catch {
        throw new Error(`${file.name} is not a Lumen project file`);
      }
      const created = await api.projects.import(content);
      notify(`Imported "${created.name}"`);
      await app.reloadProjects();
    } catch (e) {
      showError(e);
    }
  }

  function seconds(ms: number): string {
    return `${(ms / 1000).toFixed(ms % 1000 === 0 ? 0 : 1)} s`;
  }
</script>

<section class="view">
  <header class="head">
    <div>
      <h1>Projects</h1>
    </div>
    <div class="head-actions">
      <button class="btn ghost" type="button" onclick={() => importInput?.click()}>Import…</button>
      <button class="btn primary" type="button" onclick={openCreate}>+ New project</button>
    </div>
    <input class="hidden" type="file" accept=".json,application/json" bind:this={importInput} onchange={importFile}
      aria-label="Project file to import" />
  </header>

  {#if app.projects.length === 0}
    <div class="glass empty">
      <h2>No projects yet</h2>
      <p>Create your first project to start patching fixtures.</p>
      <button class="btn primary" type="button" onclick={openCreate}>+ New project</button>
    </div>
  {:else}
    <div class="cards">
      {#each app.projects as project (project.id)}
        <article class="card glass" class:active={project.active}>
          <div class="title">
            <h2>{project.name}</h2>
            {#if project.active}<span class="badge accent">Active</span>{/if}
          </div>
          <p class="dim">{project.venue ?? 'No venue'}</p>
          <dl>
            <div><dt>Fade in</dt><dd class="mono">{seconds(project.fadeInMs)}</dd></div>
            <div><dt>Fade out</dt><dd class="mono">{seconds(project.fadeOutMs)}</dd></div>
            <div><dt>Curve</dt><dd>{curveLabel(project.fadeCurve)}</dd></div>
          </dl>
          <div class="actions">
            {#if project.active}
              <button class="btn small primary" type="button" onclick={() => navigate('/setup/patch')}>Open patch</button>
            {:else}
              <button class="btn small" type="button" onclick={() => app.activate(project.id)}>Activate</button>
            {/if}
            <button class="btn small ghost" type="button" onclick={() => openEdit(project)}>Edit</button>
            <a class="btn small ghost" href={`/api/projects/${project.id}/export`} download
              title="Download the project with patch, groups and scenes">Export</a>
            <button class="btn small ghost danger" type="button"
              onclick={() => { deleting = project; deleteOpen = true; }}>Delete</button>
          </div>
        </article>
      {/each}
    </div>
  {/if}

  <p class="dim help">
    A project is one show: its universes, patch and scenes. The active project drives the light.
  </p>
</section>

<Modal bind:open={formOpen} title={editing ? 'Edit project' : 'New project'}>
  <form id="project-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
    <label class="field">Name<input bind:value={form.name} placeholder="The Tempest" required /></label>
    <label class="field">Venue<input bind:value={form.venue} placeholder="City Theatre (optional)" /></label>
    <div class="row">
      <label class="field">Fade in (s)<input type="number" min="0" max="600" step="0.1" bind:value={form.fadeIn} /></label>
      <label class="field">Fade out (s)<input type="number" min="0" max="600" step="0.1" bind:value={form.fadeOut} /></label>
      <label class="field">Curve
        <select bind:value={form.fadeCurve}>
          {#each FADE_CURVES as curve (curve.id)}<option value={curve.id} title={curve.hint}>{curve.label}</option>{/each}
        </select>
      </label>
    </div>
    <p class="field"><span class="hint">Fade times apply to every scene change of this project.</span></p>
  </form>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (formOpen = false)}>Cancel</button>
    <button class="btn primary" type="submit" form="project-form">{editing ? 'Save' : 'Create'}</button>
  {/snippet}
</Modal>

<Modal bind:open={deleteOpen} title="Delete project">
  <p>Delete <strong>{deleting?.name}</strong>? Its universes, patch and groups are removed as well.</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (deleteOpen = false)}>Cancel</button>
    <button class="btn danger" type="button" onclick={confirmDelete}>Delete</button>
  {/snippet}
</Modal>

<style>
  .help {
    margin: 0;
    max-width: 820px;
    font-size: 12px;
  }

  .head-actions {
    display: flex;
    gap: 8px;
  }

  .hidden {
    display: none;
  }

  .view {
    display: flex;
    flex-direction: column;
    gap: 20px;
    padding: 12px 8px;
  }

  .head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: var(--gap);
  }

  .card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 18px;
    transition: box-shadow 0.2s, border-color 0.2s;
  }

  .card.active {
    border-color: var(--accent);
    box-shadow: var(--shadow), 0 0 0 1px var(--accent-soft), 0 0 40px var(--accent-soft);
  }

  .title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .card p {
    margin: 0;
  }

  dl {
    display: flex;
    gap: 24px;
    margin: 4px 0;
  }

  dt {
    font-size: 11px;
    color: var(--text-faint);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  dd {
    margin: 2px 0 0;
  }

  .actions {
    display: flex;
    gap: 6px;
    margin-top: auto;
  }
</style>
