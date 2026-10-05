<script lang="ts">
  import Modal from '../components/Modal.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import SceneCompositions from '../components/SceneCompositions.svelte';
  import SceneExtraKeys from '../components/SceneExtraKeys.svelte';
  import { api } from '../lib/api';
  import { modesOf, previewOf, rgbToCss, swatchesOf } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import { kindIcon } from '../lib/compositions';
  import { numberAt, numberBetween } from '../lib/sceneNumbers';
  import { parseScript } from '../lib/script';
  import {
    curveLabel, FADE_CURVES, fadeEnded, fadeInProgress, sceneRequestOf, secondsToMs, timingLabel,
  } from '../lib/sceneTiming';
  import { shortTime } from '../lib/format';
  import { programmerSelection } from '../lib/selection.svelte';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import type { AttributeGroup, FadeCurve, Fixture, Group, Scene, SceneRevision } from '../lib/types';

  const ATTRIBUTE_GROUPS: { id: AttributeGroup; label: string }[] = [
    { id: 'intensity', label: 'Intensity' },
    { id: 'color', label: 'Color' },
    { id: 'position', label: 'Position' },
    { id: 'beam', label: 'Beam' },
    { id: 'other', label: 'Other' },
  ];

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedId = $state<number | null>(null);
  let recordOpen = $state(false);
  let overwriteOpen = $state(false);
  let deleteOpen = $state(false);
  /** options of “update from programmer”: replace the whole look or merge parts of the programmer */
  let update = $state({ merge: false, onlySelected: false, groups: [] as AttributeGroup[] });
  let revisions = $state<SceneRevision[]>([]);
  let form = $state({
    number: '',
    name: '',
    cueNote: '',
    autoFollow: false,
    autoFollowSeconds: 5,
    keyOnly: false,
    // empty (null) = the project's time
    fadeInSeconds: null as number | null,
    fadeOutSeconds: null as number | null,
    delaySeconds: null as number | null,
    // empty = the project's curve
    fadeCurve: '' as FadeCurve | '',
    script: '',
  });
  let fades = $state({ fadeIn: 3, fadeOut: 3, fadeCurve: 'linear' as FadeCurve });

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);
  const fadeInMs = $derived(app.active?.fadeInMs ?? null);
  const fadeOutMs = $derived(app.active?.fadeOutMs ?? null);
  const fadeCurve = $derived(app.active?.fadeCurve ?? null);
  const selected = $derived(scenes.find((s) => s.id === selectedId) ?? null);
  const scriptStats = $derived.by(() => {
    const script = parseScript(form.script);
    return { lines: script.lineCount, notes: script.noteCount };
  });
  /** the scene GO comes from in the show – a rehearsed fade starts there */
  const previousScene = $derived.by(() => {
    if (!selected || selected.keyOnly) return null;
    const index = cueScenes.indexOf(selected);
    return index > 0 ? cueScenes[index - 1] : null;
  });

  // progress of a rehearsed fade, interpolated from the moment the status arrived
  let now = $state(performance.now());
  const rehearsal = $derived(stagePreview.status.fade);
  const rehearsalElapsed = $derived(rehearsal ? rehearsal.elapsedMs + (now - stagePreview.receivedAt) : 0);
  const rehearsalRunning = $derived(rehearsal !== null && !fadeEnded(rehearsalElapsed, rehearsal));

  $effect(() => {
    if (rehearsal === null) return;
    let frame = requestAnimationFrame(function tick() {
      now = performance.now();
      if (!fadeEnded(rehearsalElapsed, rehearsal)) frame = requestAnimationFrame(tick);
    });
    return () => cancelAnimationFrame(frame);
  });
  /** the cue list played by GO, and the scenes that only extra keys play */
  const cueScenes = $derived(scenes.filter((s) => !s.keyOnly));
  const keyScenes = $derived(scenes.filter((s) => s.keyOnly));
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const nextNumber = $derived(
    scenes.length === 0 ? '1' : String(Math.floor(Number(scenes[scenes.length - 1].number)) + 1),
  );

  $effect(() => {
    void live.scenesRevision;
    void live.patchRevision;
    void live.compositionsRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    void live.scenesRevision;
    if (projectId !== null && selectedId !== null) loadRevisions(projectId, selectedId);
    else revisions = [];
  });

  async function loadRevisions(projectId: number, sceneId: number) {
    try {
      const loaded = await api.scenes.revisions(projectId, sceneId);
      if (selectedId === sceneId) revisions = loaded;
    } catch (e) {
      showError(e);
    }
  }

  async function restore(revision: SceneRevision) {
    if (!project || !selected) return;
    try {
      await api.scenes.restore(project.id, selected.id, revision.id);
      notify(`Scene ${selected.number}: look from ${shortTime(new Date(revision.createdAt))} restored`);
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  $effect(() => {
    // only when the stored fade times change, so unsaved edits survive unrelated project reloads
    if (fadeInMs !== null && fadeOutMs !== null && fadeCurve !== null) {
      fades = { fadeIn: fadeInMs / 1000, fadeOut: fadeOutMs / 1000, fadeCurve };
    }
  });

  async function load(projectId: number) {
    try {
      [scenes, fixtures, groups] = await Promise.all([
        api.scenes.list(projectId),
        api.fixtures.list(projectId),
        api.groups.list(projectId),
      ]);
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
      keyOnly: scene.keyOnly,
      fadeInSeconds: scene.fadeInMs !== null ? scene.fadeInMs / 1000 : null,
      fadeOutSeconds: scene.fadeOutMs !== null ? scene.fadeOutMs / 1000 : null,
      delaySeconds: scene.delayMs > 0 ? scene.delayMs / 1000 : null,
      fadeCurve: scene.fadeCurve ?? '',
      script: scene.script,
    };
  }

  function swatches(scene: Scene) {
    return swatchesOf(fixtures, modes, scene.values);
  }

  async function save() {
    if (!project || !selected) return;
    try {
      const scene = await api.scenes.update(project.id, selected.id, {
        number: form.number,
        name: form.name,
        cueNote: form.cueNote || null,
        autoFollowMs: form.autoFollow ? Math.round(form.autoFollowSeconds * 1000) : null,
        keyOnly: form.keyOnly,
        fadeInMs: secondsToMs(form.fadeInSeconds),
        fadeOutMs: secondsToMs(form.fadeOutSeconds),
        delayMs: secondsToMs(form.delaySeconds),
        fadeCurve: form.fadeCurve || null,
        script: form.script,
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

  function openUpdate() {
    update = { merge: false, onlySelected: false, groups: [] };
    overwriteOpen = true;
  }

  async function overwrite() {
    if (!project || !selected) return;
    try {
      await api.scenes.record(project.id, selected.id, update.merge
        ? { merge: true, fixtureIds: update.onlySelected ? programmerSelection.ids : null, groups: update.groups }
        : { merge: false });
      notify(`Scene ${selected.number} ${update.merge ? 'merged with' : 'updated from'} the programmer`);
      overwriteOpen = false;
      await load(project.id);
    } catch (e) {
      showError(e);
    }
  }

  /** A free number right after the scene, among the scenes of its list; null (with an error shown) if none fits. */
  function numberAfter(scene: Scene): string | null {
    const list = scene.keyOnly ? keyScenes : cueScenes;
    const next = list[list.indexOf(scene) + 1];
    const number = numberBetween(scene.number, next?.number ?? null);
    if (number === null) {
      showError(new Error(`No free scene number after ${scene.number} – renumber the next scene first`));
    }
    return number;
  }

  /** Inserts an empty scene after the selected one (number in between), or at the end. */
  async function addEmpty() {
    if (!project) return;
    let number: string | null = null;
    if (selected) {
      number = numberAfter(selected);
      if (number === null) return;
    }
    try {
      const scene = await api.scenes.create(project.id, {
        number,
        name: selected?.keyOnly ? 'Key look' : 'Blackout',
        cueNote: null,
        autoFollowMs: null,
        keyOnly: selected?.keyOnly ?? false,
      });
      await load(project.id);
      select(scene);
    } catch (e) {
      showError(e);
    }
  }

  /** Copies the selected scene right after it, with its look, compositions and extra keys. */
  async function duplicate() {
    if (!project || !selected) return;
    const number = numberAfter(selected);
    if (number === null) return;
    try {
      const scene = await api.scenes.duplicate(project.id, selected.id, number);
      notify(`Copied to scene ${scene.number}`);
      await load(project.id);
      select(scene);
    } catch (e) {
      showError(e);
    }
  }

  // --- reorder by drag & drop: the scene gets a number between its new neighbors ------------------------------

  let listElement: HTMLElement | undefined = $state();
  /** scene being dragged and the position among the other scenes it would be dropped at */
  let drag = $state<{ id: number; from: number; to: number } | null>(null);

  function startDrag(event: PointerEvent, index: number, scene: Scene) {
    if (event.button !== 0) return;
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    drag = { id: scene.id, from: index, to: index };
  }

  function moveDrag(event: PointerEvent) {
    if (!drag || !listElement) return;
    const rows = [...listElement.querySelectorAll<HTMLElement>('.cue-row:not(.key-row)')];
    // position among the other scenes: count rows (except the dragged one) whose middle is above the pointer
    let to = 0;
    rows.forEach((row, i) => {
      const rect = row.getBoundingClientRect();
      if (i !== drag!.from && event.clientY > rect.top + rect.height / 2) to++;
    });
    drag = { ...drag, to };
  }

  async function endDrag() {
    if (!drag) return;
    const { id, from, to } = drag;
    drag = null;
    if (to !== from) await moveTo(id, to);
  }

  /** Keyboard alternative: Alt+↑/↓ on the handle moves the scene by one place. */
  function onHandleKey(event: KeyboardEvent, index: number, scene: Scene) {
    if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return;
    event.preventDefault();
    const to = index + (event.key === 'ArrowUp' ? -1 : 1);
    if (to >= 0 && to < cueScenes.length) moveTo(scene.id, to);
  }

  async function moveTo(sceneId: number, to: number) {
    const scene = scenes.find((s) => s.id === sceneId);
    if (!project || !scene) return;
    // only among the cue list; scenes for extra keys keep their numbers
    const others = cueScenes.filter((s) => s.id !== sceneId).map((s) => s.number);
    const number = numberAt(others, to);
    if (number === null) {
      showError(new Error('No free scene number at that place – renumber a neighbor first'));
      return;
    }
    try {
      const updated = await api.scenes.update(project.id, scene.id, { ...sceneRequestOf(scene), number });
      notify(`Scene “${updated.name}” is now number ${updated.number}`);
      await load(project.id);
      if (selectedId === updated.id) select(updated);
    } catch (e) {
      showError(e);
    }
  }

  /** Where the drop line is drawn: before the row with this index (in the full list), or after the last. */
  function dropBefore(index: number): boolean {
    if (!drag || drag.to === drag.from) return false;
    const target = drag.to >= drag.from ? drag.to + 1 : drag.to;
    return index === target;
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
        fadeCurve: fades.fadeCurve,
      });
      await app.reloadProjects();
      notify('Fade times saved');
    } catch (e) {
      showError(e);
    }
  }

  const programmerHasValues = $derived(Object.keys(live.programmer).length > 0);
</script>

{#snippet cueContent(scene: Scene, i: number)}
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
            {#if stagePreview.status.sceneId === scene.id}
              <span class="on-stage-tag" title="This scene is playing on stage (preview)">
                <span class="pulse" aria-hidden="true"></span>ON STAGE
              </span>
            {/if}
            {#if scene.compositions.length > 0}
              <span class="badge" title={scene.compositions.map((c) => c.name).join(', ')}>
                {scene.compositions.map((c) => kindIcon(c.kind)).join(' ')}
              </span>
            {/if}
            {#if scene.script && !scene.keyOnly}
              <span class="badge" title="Has a script">📜</span>
            {/if}
            {#if timingLabel(scene) && !scene.keyOnly}
              <span class="badge" title="Own fade times">⏱ {timingLabel(scene)}</span>
            {/if}
            {#if scene.autoFollowMs !== null}
              <span class="badge accent" title="Continues automatically">⟳ {scene.autoFollowMs / 1000} s{!scene.keyOnly && i === cueScenes.length - 1 ? ' (last – ignored)' : ''}</span>
            {/if}
          {/snippet}


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
        <p class="dim">
          The cue list of the show, ordered by number. Drag a scene by ⋮⋮ to move it – it gets a number between its new
          neighbors (like 2.5). “+ Empty scene” inserts after the selected scene.
        </p>
      </div>
      <form class="fades glass" onsubmit={(e) => { e.preventDefault(); saveFades(); }}>
        <label>Fade in <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeIn} /> s</label>
        <label>Fade out <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeOut} /> s</label>
        <label>Curve
          <select bind:value={fades.fadeCurve}>
            {#each FADE_CURVES as curve (curve.id)}<option value={curve.id} title={curve.hint}>{curve.label}</option>{/each}
          </select>
        </label>
        <button class="btn small" type="submit"
          disabled={fades.fadeIn * 1000 === project.fadeInMs && fades.fadeOut * 1000 === project.fadeOutMs
            && fades.fadeCurve === project.fadeCurve}>Save</button>
      </form>
      <div class="actions">
        <button class="btn" type="button" onclick={() => navigate('/print/cues')} disabled={scenes.length === 0}
          title="Printable cue list for the operator – or save it as PDF">🖨 Cue list</button>
        <button class="btn" type="button" onclick={addEmpty}>+ Empty scene</button>
        <button class="btn record" type="button" onclick={() => (recordOpen = true)} disabled={!programmerHasValues}
          title={programmerHasValues ? '' : 'Set some values in the programmer first'}>⏺ Record scene</button>
      </div>
    </header>

    <div class="layout">
      <div class="list glass" bind:this={listElement}>
        {#if scenes.length === 0}
          <div class="empty">
            <h2>No scenes yet</h2>
            <p>Build a look in the programmer, then record it as a scene.</p>
            <button class="btn primary" type="button" onclick={() => navigate('/setup/programmer')}>Open programmer</button>
          </div>
        {/if}
        {#each cueScenes as scene, i (scene.id)}
          <div class="cue-row" class:dragging={drag?.id === scene.id} class:drop-before={dropBefore(i)}>
          <button type="button" class="handle" aria-label="Move scene {scene.number} (drag, or Alt+↑/↓)"
            title="Drag to move – the scene gets a number between its new neighbors"
            onpointerdown={(e) => startDrag(e, i, scene)} onpointermove={moveDrag} onpointerup={endDrag}
            onpointercancel={() => (drag = null)} onkeydown={(e) => onHandleKey(e, i, scene)}>⋮⋮</button>
          <button type="button" class="cue" class:selected={scene.id === selectedId}
            class:on-stage={stagePreview.status.sceneId === scene.id} onclick={() => select(scene)}>
            {@render cueContent(scene, i)}
          </button>
          </div>
        {/each}
        {#if drag && drag.to !== drag.from && drag.to === cueScenes.length - 1}<div class="drop-end"></div>{/if}
        {#if keyScenes.length > 0}
          <h3 class="key-scenes-head" title="GO skips these scenes – they are played by extra keys">Scenes for extra keys</h3>
          {#each keyScenes as scene, i (scene.id)}
            <div class="cue-row key-row">
              <span class="handle-space" aria-hidden="true"></span>
              <button type="button" class="cue" class:selected={scene.id === selectedId}
                class:on-stage={stagePreview.status.sceneId === scene.id} onclick={() => select(scene)}>
                {@render cueContent(scene, i)}
              </button>
            </div>
          {/each}
        {/if}
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
            {#if !form.keyOnly}
              <label class="field">Script
                <textarea bind:value={form.script} rows="8"
                  placeholder={'Text spoken on stage during this scene.\n**Enter Juliet** – stage directions in two asterisks.'}
                ></textarea>
              </label>
              <p class="hint dim">
                {#if scriptStats.lines > 0 || scriptStats.notes > 0}
                  {scriptStats.lines} {scriptStats.lines === 1 ? 'sentence' : 'sentences'}
                  {#if scriptStats.notes > 0}· {scriptStats.notes} stage {scriptStats.notes === 1 ? 'direction' : 'directions'}{/if}
                  –
                {/if}
                In show mode ↑/↓ step through the sentences; <span class="mono">**text**</span> is skipped.
              </p>
            {/if}
            <label class="check">
              <input type="checkbox" bind:checked={form.autoFollow} />
              Continue automatically after
              <input class="seconds" type="number" min="0.1" max="3600" step="0.1" bind:value={form.autoFollowSeconds}
                disabled={!form.autoFollow} /> s
            </label>
            <p class="hint dim">The time starts when the fade-in has finished. Without it the scene stays until GO.</p>
            {#if !form.keyOnly}
              <fieldset class="timing">
                <legend>Fade times of the GO into this scene</legend>
                <label>In <input class="seconds" type="number" min="0" max="600" step="0.1"
                  bind:value={form.fadeInSeconds} placeholder={String(project.fadeInMs / 1000)} /> s</label>
                <label>Out <input class="seconds" type="number" min="0" max="600" step="0.1"
                  bind:value={form.fadeOutSeconds} placeholder={String(project.fadeOutMs / 1000)} /> s</label>
                <label>Delay <input class="seconds" type="number" min="0" max="600" step="0.1"
                  bind:value={form.delaySeconds} placeholder="0" /> s</label>
                <label>Curve
                  <select bind:value={form.fadeCurve}>
                    <option value="">Project's ({curveLabel(project.fadeCurve)})</option>
                    {#each FADE_CURVES as curve (curve.id)}<option value={curve.id} title={curve.hint}>{curve.label}</option>{/each}
                  </select>
                </label>
              </fieldset>
              <p class="hint dim">
                Empty = the project's time. “Out” fades the scene before, “Delay” holds back the fade-in – e.g. In 0 and
                Out 0 for a flash.
              </p>
            {/if}
            <label class="check">
              <input type="checkbox" bind:checked={form.keyOnly} />
              Only for extra keys <span class="hint dim">(GO skips this scene)</span>
            </label>
            <button class="btn primary" type="submit">Save</button>
          </form>

          <section>
            <h3>Look</h3>
            <div class="buttons">
              <button class="btn" type="button" onclick={() => selected && loadIntoProgrammer(selected)}>Load into programmer</button>
              {#if stagePreview.status.sceneId === selected.id}
                <button class="btn primary" type="button" onclick={() => stagePreview.stop()}
                  title="Stop playing this scene on stage">■ Stop showing</button>
              {:else}
                <button class="btn" type="button" onclick={() => projectId !== null && stagePreview.showScene(projectId, selected.id)}
                  title="Play the scene with its compositions on stage, above the playback">▶ Show on stage</button>
              {/if}
              {#if !selected.keyOnly}
                <button class="btn" type="button"
                  onclick={() => projectId !== null && selected && stagePreview.rehearseFade(projectId, selected.id)}
                  title="Play the GO into this scene on stage: from {previousScene ? `scene ${previousScene.number}` : 'the dark stage'} with the fade times of the show">
                  ⇢ Rehearse fade
                </button>
              {/if}
              <button class="btn record-outline" type="button" onclick={openUpdate} disabled={!programmerHasValues}>⏺ Update from programmer</button>
              {#if revisions.length > 0}
                <button class="btn" type="button" onclick={() => restore(revisions[0])}
                  title="Restore the look from before “{revisions[0].reason}” ({shortTime(new Date(revisions[0].createdAt))})">↶ Undo</button>
              {/if}
            </div>
            {#if rehearsal && rehearsalRunning && stagePreview.status.sceneId === selected.id}
              {@const from = scenes.find((s) => s.id === rehearsal.fromSceneId)}
              {@const delayLeft = Math.max(0, rehearsal.delayMs - rehearsalElapsed)}
              <div class="rehearsal" role="status">
                <div class="small">
                  {from ? `${from.number} ${from.name}` : 'Dark stage'} → {selected.number} {selected.name}
                  <span class="mono dim">
                    · {delayLeft > 0
                      ? `fade in starts in ${(delayLeft / 1000).toFixed(1)} s`
                      : `${(Math.max(0, rehearsal.delayMs + rehearsal.fadeInMs - rehearsalElapsed) / 1000).toFixed(1)} s left`}
                  </span>
                </div>
                <div class="bar">
                  <span style="width: {fadeInProgress(rehearsalElapsed, rehearsal.fadeInMs, rehearsal.delayMs) * 100}%"></span>
                </div>
              </div>
            {/if}
            {#if stagePreview.status.sceneId === selected.id && programmerHasValues}
              <p class="hint warning-text">The programmer is on top of the stage preview – clear it to see the scene as it is.</p>
            {/if}
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

          {#if revisions.length > 0}
            <section>
              <h3>History</h3>
              <ul class="revisions">
                {#each revisions as revision (revision.id)}
                  <li>
                    <span class="mono">{shortTime(new Date(revision.createdAt))}</span>
                    <span class="revision-reason dim">before “{revision.reason}”</span>
                    <span class="swatches" aria-hidden="true">
                      {#each swatchesOf(fixtures, modes, revision.values) as swatch, j (j)}
                        <span class="swatch" style="--color: {swatch.color}; --intensity: {swatch.intensity}"></span>
                      {:else}
                        <span class="dark">dark</span>
                      {/each}
                    </span>
                    <button class="btn small" type="button" onclick={() => restore(revision)}
                      title="Put this look back – the current look stays in the history">Restore</button>
                  </li>
                {/each}
              </ul>
            </section>
          {/if}

          {#if projectId !== null}
            <SceneCompositions {projectId} sceneId={selected.id} {fixtures} {groups} {modes} />
            {#if !selected.keyOnly}
              <SceneExtraKeys {projectId} sceneId={selected.id} />
            {/if}
          {/if}

          <section class="buttons">
            <button class="btn small" type="button" onclick={duplicate}
              title="Copy look, fade times, script, compositions and extra keys to a new scene right after this one">⧉ Duplicate</button>
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
  <div class="update-options">
    <p>Take the programmer into scene <strong>{selected?.number} “{selected?.name}”</strong>:</p>
    <label class="check">
      <input type="radio" name="update-mode" checked={!update.merge} onchange={() => (update.merge = false)} />
      Replace the whole look <span class="hint dim">– fixtures without programmer values go dark</span>
    </label>
    <label class="check">
      <input type="radio" name="update-mode" checked={update.merge} onchange={() => (update.merge = true)} />
      Merge <span class="hint dim">– take only what the programmer sets, keep the rest of the scene</span>
    </label>
    {#if update.merge}
      <fieldset class="merge-filter">
        <legend>Only these attributes <span class="hint dim">(none = all)</span></legend>
        {#each ATTRIBUTE_GROUPS as group (group.id)}
          <label class="check">
            <input type="checkbox" checked={update.groups.includes(group.id)} onchange={(e) => {
              const on = (e.currentTarget as HTMLInputElement).checked;
              update.groups = on ? [...update.groups, group.id] : update.groups.filter((g) => g !== group.id);
            }} />
            {group.label}
          </label>
        {/each}
      </fieldset>
      <label class="check">
        <input type="checkbox" bind:checked={update.onlySelected} disabled={programmerSelection.ids.length === 0} />
        Only the {programmerSelection.ids.length} fixtures selected in the programmer
      </label>
    {/if}
    <p class="hint dim">The look before stays in the history of the scene – “↶ Undo” puts it back.</p>
  </div>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (overwriteOpen = false)}>Cancel</button>
    <button class="btn record" type="button" onclick={overwrite}>⏺ {update.merge ? 'Merge' : 'Replace'}</button>
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
  .update-options {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .update-options p {
    margin: 0;
  }

  .merge-filter {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 16px;
    margin: 0;
    padding: 8px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .revisions {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .revisions li {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.85rem;
  }

  .revision-reason {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .warning-text {
    color: var(--warning);
  }

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
    /* half for the list, half for the selected scene – the editor has far more content */
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
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
    /* number and text, then as many badges as the scene has, all in one row */
    grid-template-columns: 64px minmax(0, 1fr);
    grid-auto-flow: column;
    grid-auto-columns: auto;
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

  .cue-row {
    position: relative;
    display: flex;
    align-items: stretch;
  }

  .cue-row + .cue-row {
    border-top: 1px solid var(--border);
  }

  .cue-row .cue {
    flex: 1;
    min-width: 0;
  }

  .cue-row.dragging {
    opacity: 0.4;
  }

  .cue-row.drop-before::before,
  .drop-end {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: -2px;
    height: 3px;
    border-radius: 2px;
    background: var(--accent);
    box-shadow: 0 0 10px var(--accent-glow);
  }

  .drop-end {
    position: relative;
    top: 0;
  }

  .key-scenes-head {
    margin: 18px 10px 6px;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-dim);
  }

  .handle-space {
    flex: none;
    width: 26px;
  }

  .handle {
    flex: none;
    width: 26px;
    border: 0;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text-faint);
    font: inherit;
    letter-spacing: -2px;
    cursor: grab;
    touch-action: none;
  }

  .handle:hover,
  .handle:focus-visible {
    color: var(--accent);
  }

  .cue:hover {
    background: var(--panel-raised);
  }

  /* playing on stage as a preview: glowing edge, independent of the selection */
  .cue.on-stage {
    box-shadow: inset 3px 0 0 var(--accent), -4px 0 14px -6px var(--accent-glow);
  }

  .on-stage-tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 8px;
    border-radius: 6px;
    background: var(--accent);
    color: var(--accent-ink);
    font-size: 10px;
    font-weight: 800;
    letter-spacing: 0.1em;
    white-space: nowrap;
  }

  .on-stage-tag .pulse {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--accent-ink);
    animation: on-stage-pulse 1.2s ease-in-out infinite;
  }

  @keyframes on-stage-pulse {
    50% {
      opacity: 0.25;
    }
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
    /* scrolls on its own, so the end of a long scene never hides behind the master bar */
    max-height: calc(100vh - 290px); /* fits between the page header and the master bar */
    overflow-y: auto;
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

  .rehearsal {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .rehearsal .bar {
    height: 6px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }

  .rehearsal .bar span {
    display: block;
    height: 100%;
    background: var(--accent);
  }

  .timing {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    margin: 0;
    padding: 8px 10px 10px;
    border: 1px solid var(--border);
    border-radius: 9px;
    font-size: 13px;
  }

  .timing legend {
    padding: 0 4px;
    font-size: 12px;
    color: var(--text-dim);
  }

  .timing label {
    display: flex;
    align-items: center;
    gap: 6px;
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
      max-height: none;
    }
  }
</style>
