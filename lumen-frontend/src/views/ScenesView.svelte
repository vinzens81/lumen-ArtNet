<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import { api } from '../lib/api';
  import { previewOf, rgbToCss } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import type { Fixture, Mode, Scene } from '../lib/types';

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let selectedId = $state<number | null>(null);
  let recordOpen = $state(false);
  let overwriteOpen = $state(false);
  let deleteOpen = $state(false);
  let form = $state({ number: '', name: '', cueNote: '', autoFollow: false, autoFollowSeconds: 5 });
  let fades = $state({ fadeIn: 3, fadeOut: 3 });

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);
  const fadeInMs = $derived(app.active?.fadeInMs ?? null);
  const fadeOutMs = $derived(app.active?.fadeOutMs ?? null);
  const selected = $derived(scenes.find((s) => s.id === selectedId) ?? null);
  const modes = $derived.by(() => {
    const result = new Map<number, Mode>();
    for (const fixture of fixtures) {
      const mode = app.fixtureType(fixture.fixtureTypeId)?.modes.find((m) => m.name === fixture.mode);
      if (mode) result.set(fixture.id, mode);
    }
    return result;
  });
  const nextNumber = $derived(
    scenes.length === 0 ? '1' : String(Math.floor(Number(scenes[scenes.length - 1].number)) + 1),
  );

  $effect(() => {
    void live.scenesRevision;
    void live.patchRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    // only when the stored fade times change, so unsaved edits survive unrelated project reloads
    if (fadeInMs !== null && fadeOutMs !== null) fades = { fadeIn: fadeInMs / 1000, fadeOut: fadeOutMs / 1000 };
  });

  async function load(projectId: number) {
    try {
      [scenes, fixtures] = await Promise.all([api.scenes.list(projectId), api.fixtures.list(projectId)]);
      if (selectedId !== null && !scenes.some((s) => s.id === selectedId)) selectedId = null;
    } catch (e) {
      showError(e);
    }
  }

  function select(scene: Scene) {
    selectedId = scene.id;
    form = {
      number: scene.number,
      name: scene.name,
      cueNote: scene.cueNote ?? '',
      autoFollow: scene.autoFollowMs !== null,
      autoFollowSeconds: scene.autoFollowMs !== null ? scene.autoFollowMs / 1000 : 5,
    };
  }

  /** Glowing swatches of the lit fixtures, in patch order. */
  function swatches(scene: Scene): { color: string; intensity: number }[] {
    return fixtures
      .filter((f) => scene.values[f.id] && modes.has(f.id))
      .sort((a, b) => a.universeId - b.universeId || a.address - b.address)
      .map((f) => previewOf(modes.get(f.id)!, scene.values[f.id]))
      .filter((p) => p.intensity > 0.01)
      .map((p) => ({ color: rgbToCss(p.color), intensity: p.intensity }));
  }

  async function save() {
    if (!project || !selected) return;
    try {
      const scene = await api.scenes.update(project.id, selected.id, {
        number: form.number,
        name: form.name,
        cueNote: form.cueNote || null,
        autoFollowMs: form.autoFollow ? Math.round(form.autoFollowSeconds * 1000) : null,
      });
      notify(`Saved scene ${scene.number}`);
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function loadIntoProgrammer(scene: Scene) {
    if (!project) return;
    try {
      await api.scenes.load(project.id, scene.id);
      notify(`Scene ${scene.number} loaded into the programmer`);
    } catch (e) {
      showError(e);
    }
  }

  async function overwrite() {
    if (!project || !selected) return;
    try {
      await api.scenes.record(project.id, selected.id);
      notify(`Scene ${selected.number} updated from the programmer`);
      overwriteOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function addEmpty() {
    if (!project) return;
    try {
      const scene = await api.scenes.create(project.id, { name: 'Blackout', cueNote: null, autoFollowMs: null });
      await load(project.id);
      select(scene);
    } catch (e) {
      showError(e);
    }
  }

  async function remove() {
    if (!project || !selected) return;
    try {
      await api.scenes.remove(project.id, selected.id);
      deleteOpen = false;
      selectedId = null;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  async function saveFades() {
    if (!project) return;
    try {
      await api.projects.update(project.id, {
        name: project.name,
        venue: project.venue,
        fadeInMs: Math.round(fades.fadeIn * 1000),
        fadeOutMs: Math.round(fades.fadeOut * 1000),
      });
      await app.reloadProjects();
      notify('Fade times saved');
    } catch (e) {
      showError(e);
    }
  }

  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);
</script>

{#if !project}
  <div class="glass empty">
    <h2>No active project</h2>
    <button class="btn primary" type="button" onclick={() => navigate('/setup/projects')}>Go to projects</button>
  </div>
{:else}
  <section class="view">
    <header class="head">
      <div>
        <h1>Scenes</h1>
        <p class="dim">The cue list of the show, ordered by number. Use decimals like 2.5 to insert between scenes.</p>
      </div>
      <form class="fades glass" onsubmit={(e) => { e.preventDefault(); saveFades(); }}>
        <label>Fade in <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeIn} /> s</label>
        <label>Fade out <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeOut} /> s</label>
        <button class="btn small" type="submit"
          disabled={fades.fadeIn * 1000 === project.fadeInMs && fades.fadeOut * 1000 === project.fadeOutMs}>Save</button>
      </form>
      <div class="actions">
        <button class="btn" type="button" onclick={addEmpty}>+ Empty scene</button>
        <button class="btn record" type="button" onclick={() => (recordOpen = true)} disabled={!programmerHasValues}
          title={programmerHasValues ? '' : 'Set some values in the programmer first'}>⏺ Record scene</button>
      </div>
    </header>

    <div class="layout">
      <div class="list glass">
        {#if scenes.length === 0}
          <div class="empty">
            <h2>No scenes yet</h2>
            <p>Build a look in the programmer, then record it as a scene.</p>
            <button class="btn primary" type="button" onclick={() => navigate('/setup/programmer')}>Open programmer</button>
          </div>
        {/if}
        {#each scenes as scene, i (scene.id)}
          <button type="button" class="cue" class:selected={scene.id === selectedId} onclick={() => select(scene)}>
            <span class="number mono">{scene.number}</span>
            <span class="text">
              <span class="name">{scene.name}</span>
              {#if scene.cueNote}<span class="note">{scene.cueNote}</span>{/if}
            </span>
            <span class="swatches" aria-hidden="true">
              {#each swatches(scene) as swatch, j (j)}
                <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
              {:else}
                <span class="dark">dark</span>
              {/each}
            </span>
            {#if scene.autoFollowMs !== null}
              <span class="badge accent" title="Continues automatically">⟳ {scene.autoFollowMs / 1000} s{i === scenes.length - 1 ? ' (last – ignored)' : ''}</span>
            {/if}
          </button>
        {/each}
      </div>

      <aside class="editor glass">
        {#if !selected}
          <p class="dim">Select a scene to edit it.</p>
        {:else}
          <form onsubmit={(e) => { e.preventDefault(); save(); }}>
            <div class="row">
              <label class="field number-field">Number<input bind:value={form.number} required inputmode="decimal" /></label>
              <label class="field">Name<input bind:value={form.name} required /></label>
            </div>
            <label class="field">Cue note<input bind:value={form.cueNote} placeholder="When to press GO" /></label>
            <label class="check">
              <input type="checkbox" bind:checked={form.autoFollow} />
              Continue automatically after
              <input class="seconds" type="number" min="0.1" max="3600" step="0.1" bind:value={form.autoFollowSeconds}
                disabled={!form.autoFollow} /> s
            </label>
            <p class="hint dim">The time starts when the fade-in has finished. Without it the scene stays until GO.</p>
            <button class="btn primary" type="submit">Save</button>
          </form>

          <section>
            <h3>Look</h3>
            <div class="buttons">
              <button class="btn" type="button" onclick={() => selected && loadIntoProgrammer(selected)}>Load into programmer</button>
              <button class="btn record-outline" type="button" onclick={() => (overwriteOpen = true)} disabled={!programmerHasValues}>⏺ Update from programmer</button>
            </div>
            <ul class="fixtures">
              {#each fixtures.filter((f) => selected?.values[f.id]) as fixture (fixture.id)}
                {@const preview = previewOf(modes.get(fixture.id) ?? { name: '', dmxChannelCount: 0, channels: [] }, selected.values[fixture.id])}
                <li>
                  <span class="swatch" style="--color: {rgbToCss(preview.color)}; --intensity: {preview.intensity}"></span>
                  {fixture.name}
                  <span class="mono dim">{Math.round((selected.values[fixture.id].dimmer ?? 0) * 100)}%</span>
                </li>
              {:else}
                <li class="dim">No fixtures – everything is dark in this scene.</li>
              {/each}
            </ul>
          </section>

          <section>
            <button class="btn small danger" type="button" onclick={() => (deleteOpen = true)}>Delete scene</button>
          </section>
        {/if}
      </aside>
    </div>
  </section>
{/if}

<RecordSceneModal bind:open={recordOpen} suggestedNumber={nextNumber}
  onrecorded={(scene) => project && load(project.id).then(() => select(scene))} />

<Modal bind:open={overwriteOpen} title="Update scene">
  <p>Replace the look of scene <strong>{selected?.number} “{selected?.name}”</strong> with the current programmer values?</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (overwriteOpen = false)}>Cancel</button>
    <button class="btn record" type="button" onclick={overwrite}>⏺ Update</button>
  {/snippet}
</Modal>

<Modal bind:open={deleteOpen} title="Delete scene">
  <p>Delete scene <strong>{selected?.number} “{selected?.name}”</strong>?</p>
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
    gap: 16px;
    flex-wrap: wrap;
  }

  .head > div:first-child {
    flex: 1;
    min-width: 260px;
  }

  .head p {
    margin: 6px 0 0;
  }

  .fades {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 8px 12px;
    box-shadow: none;
    font-size: 12px;
    color: var(--text-dim);
  }

  .fades input {
    width: 64px;
    min-height: 30px;
    margin: 0 4px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .actions {
    display: flex;
    gap: 8px;
  }

  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 360px;
    gap: 12px;
    align-items: start;
  }

  .list {
    display: flex;
    flex-direction: column;
    padding: 8px;
    min-width: 0;
  }

  .cue {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr) auto auto;
    align-items: center;
    gap: 14px;
    padding: 12px 14px;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .cue + .cue {
    border-top-color: var(--border);
  }

  .cue:hover {
    background: var(--panel-raised);
  }

  .cue.selected {
    border-color: var(--accent);
    background: var(--accent-soft);
  }

  .number {
    font-size: 20px;
    font-weight: 700;
    color: var(--accent);
  }

  .text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .name {
    font-weight: 600;
  }

  .note {
    overflow: hidden;
    font-size: 12px;
    color: var(--text-dim);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .swatches {
    display: flex;
    gap: 4px;
    max-width: 260px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .swatch {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--color) calc(var(--intensity) * 100%), #111);
    box-shadow: 0 0 calc(10px * var(--intensity)) var(--color);
    flex: none;
  }

  .dark {
    font-size: 11px;
    color: var(--text-faint);
  }

  .editor {
    position: sticky;
    top: 84px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }

  .editor form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .number-field {
    flex: 0 0 100px;
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  .seconds {
    width: 70px;
    min-height: 30px;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid var(--border-strong);
    background: rgba(0, 0, 0, 0.25);
    color: var(--text);
    font: inherit;
  }

  .hint {
    margin: -4px 0 0;
    font-size: 11px;
  }

  section {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .fixtures {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .fixtures li {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .fixtures .mono {
    margin-left: auto;
  }

  @media (max-width: 1000px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .editor {
      position: static;
    }
  }
</style>
