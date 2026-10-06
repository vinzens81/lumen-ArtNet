<script lang="ts">
  import FadersPanel from '../components/FadersPanel.svelte';
  import Modal from '../components/Modal.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import SceneCompositions from '../components/SceneCompositions.svelte';
  import SceneKeys from '../components/SceneKeys.svelte';
  import ScriptText from '../components/ScriptText.svelte';
  import UpdateFromProgrammerModal from '../components/UpdateFromProgrammerModal.svelte';
  import { api } from '../lib/api';
  import { modesOf, swatchesOf } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import { compositionIcon, lookMap, sceneStartValues } from '../lib/compositions';
  import { numberAt, numberBetween } from '../lib/sceneNumbers';
  import { parseScript } from '../lib/script';
  import {
    curveLabel, FADE_CURVES, fadeEnded, fadeInProgress, sceneRequestOf, secondsToMs, timingLabel,
  } from '../lib/sceneTiming';
  import { shortTime } from '../lib/format';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import type {
    Composition, FadeCurve, Fixture, Group, Look, LookRevision, RecordRequest, Scene,
  } from '../lib/types';

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedId = $state<number | null>(null);
  let recordOpen = $state(false);
  let overwriteOpen = $state(false);
  let deleteOpen = $state(false);
  let scriptPreviewOpen = $state(false);
  let revisions = $state<LookRevision[]>([]);
  let compositions = $state<Composition[]>([]);
  let looks = $state<Look[]>([]);
  let form = $state({
    number: '',
    name: '',
    cueNote: '',
    autoFollow: false,
    autoFollowSeconds: 5,
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
  /** light markers of the script being edited, each name once: every one gets light of its own */
  const sceneMarkers = $derived.by(() => {
    const seen = new Set<string>();
    return parseScript(form.script).markers.filter((m) => !seen.has(m.name) && seen.add(m.name));
  });
  const scriptStats = $derived.by(() => {
    const script = parseScript(form.script);
    return { lines: script.lineCount, notes: script.noteCount, speakers: script.speakers.length };
  });
  /** the scene GO comes from in the show – a rehearsed fade starts there */
  const previousScene = $derived.by(() => {
    if (!selected) return null;
    const index = scenes.indexOf(selected);
    return index > 0 ? scenes[index - 1] : null;
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
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const looksById = $derived(lookMap(looks));
  /** the look of a simple scene: its first composition plays one look – “Update from programmer” changes it */
  const baseLook = $derived.by(() => {
    const first = selected?.compositions[0];
    const composition = first ? compositionsById.get(first.compositionId) : undefined;
    return composition?.steps.length === 1 ? looksById.get(composition.steps[0].lookId) ?? null : null;
  });
  /** the quick update works for a scene without compositions (records one) or with a simple first composition */
  const canQuickUpdate = $derived(selected !== null && (selected.compositions.length === 0 || baseLook !== null));
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const nextNumber = $derived(
    scenes.length === 0 ? '1' : String(Math.floor(Number(scenes[scenes.length - 1].number)) + 1),
  );

  $effect(() => {
    void live.scenesRevision;
    void live.patchRevision;
    void live.compositionsRevision;
    void live.looksRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    void live.looksRevision;
    const lookId = baseLook?.id ?? null;
    if (projectId !== null && lookId !== null) loadRevisions(projectId, lookId);
    else revisions = [];
  });

  async function loadRevisions(projectId: number, lookId: number) {
    try {
      const loaded = await api.looks.revisions(projectId, lookId);
      if (baseLook?.id === lookId) revisions = loaded;
    } catch (e) {
      showError(e);
    }
  }

  async function restore(revision: LookRevision) {
    if (!project || !selected || !baseLook) return;
    try {
      await api.looks.restore(project.id, baseLook.id, revision.id);
      notify(`Scene ${selected.number}: look from ${shortTime(new Date(revision.createdAt))} restored`);
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
      [scenes, fixtures, groups, compositions, looks] = await Promise.all([
        api.scenes.list(projectId),
        api.fixtures.list(projectId),
        api.groups.list(projectId),
        api.compositions.list(projectId),
        api.looks.list(projectId),
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
      fadeInSeconds: scene.fadeInMs !== null ? scene.fadeInMs / 1000 : null,
      fadeOutSeconds: scene.fadeOutMs !== null ? scene.fadeOutMs / 1000 : null,
      delaySeconds: scene.delayMs > 0 ? scene.delayMs / 1000 : null,
      fadeCurve: scene.fadeCurve ?? '',
      script: scene.script,
    };
  }

  function swatches(scene: Scene) {
    return swatchesOf(fixtures, modes, sceneStartValues(scene, compositionsById, looksById));
  }

  async function save() {
    if (!project || !selected) return;
    try {
      const scene = await api.scenes.update(project.id, selected.id, {
        number: form.number,
        name: form.name,
        cueNote: form.cueNote || null,
        autoFollowMs: form.autoFollow ? Math.round(form.autoFollowSeconds * 1000) : null,
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

  async function overwrite(request: RecordRequest) {
    if (!project || !selected) return;
    try {
      await api.scenes.record(project.id, selected.id, request);
      notify(`Scene ${selected.number} ${request.merge ? 'merged with' : 'updated from'} the programmer`);
      overwriteOpen = false;
    } catch (e) {
      showError(e);
    }
  }

  /** A free number right after the scene; null (with an error shown) if none fits. */
  function numberAfter(scene: Scene): string | null {
    const next = scenes[scenes.indexOf(scene) + 1];
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
        name: 'Blackout',
        cueNote: null,
        autoFollowMs: null,
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
    const rows = [...listElement.querySelectorAll<HTMLElement>('.cue-row')];
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
    if (to >= 0 && to < scenes.length) moveTo(scene.id, to);
  }

  async function moveTo(sceneId: number, to: number) {
    const scene = scenes.find((s) => s.id === sceneId);
    if (!project || !scene) return;
    const others = scenes.filter((s) => s.id !== sceneId).map((s) => s.number);
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
            {#if scene.compositions.length > 1 || (scene.compositions.length === 1
              && (compositionsById.get(scene.compositions[0].compositionId)?.steps.length ?? 0) > 1)}
              {@const used = scene.compositions.flatMap((use) => compositionsById.get(use.compositionId) ?? [])}
              <span class="badge" title={used.map((c) => c.name).join(', ')}>
                {used.map((c) => compositionIcon(c)).join(' ')}
              </span>
            {/if}
            {#if scene.keys.length > 0}
              <span class="badge" title="Keys {scene.keys.map((k) => k.slot).join(', ')}">⌨ {scene.keys.length}</span>
            {/if}
            {#if scene.script}
              <span class="badge" title="Has a script">📜</span>
            {/if}
            {#if timingLabel(scene)}
              <span class="badge" title="Own fade times">⏱ {timingLabel(scene)}</span>
            {/if}
            {#if scene.autoFollowMs !== null}
              <span class="badge accent" title="Continues automatically">⟳ {scene.autoFollowMs / 1000} s{i === scenes.length - 1 ? ' (last – ignored)' : ''}</span>
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
            <p>
              Build a look in the programmer and record it into a composition (one step or many), then add compositions
              to a scene. “⏺ Record scene” does all three in one go: look, composition with one step, and scene.
            </p>
            <div class="buttons">
              <button class="btn primary" type="button" onclick={() => navigate('/setup/programmer')}>Open programmer</button>
              <button class="btn" type="button" onclick={() => navigate('/setup/compositions')}>Open compositions</button>
            </div>
          </div>
        {/if}
        {#each scenes as scene, i (scene.id)}
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
        {#if drag && drag.to !== drag.from && drag.to === scenes.length - 1}<div class="drop-end"></div>{/if}
        {#if projectId !== null}
          <FadersPanel {projectId} {compositions} />
        {/if}
      </div>

      <aside class="editor glass">
        {#if !selected}
          <p class="dim">Select a scene to edit it.</p>
        {:else}
          <form id="scene-form" onsubmit={(e) => { e.preventDefault(); save(); }}>
            <div class="row">
              <label class="field number-field">Number<input bind:value={form.number} required inputmode="decimal" /></label>
              <label class="field">Name<input bind:value={form.name} required /></label>
            </div>
            <label class="field">Cue note<input bind:value={form.cueNote} placeholder="When to press GO" /></label>
              <label class="field">Script
                <textarea bind:value={form.script} rows="8"
                  placeholder={'Text spoken on stage during this scene.\nJuliet: Ay me! – a name and a colon at the start of a line marks who speaks.\n**Enter Juliet** – stage directions in two asterisks.\n//## Window (moonlight) ## – a light marker: the light of the scene changes here.'}
                ></textarea>
              </label>
              <p class="hint dim">
                {#if scriptStats.lines > 0 || scriptStats.notes > 0}
                  {scriptStats.lines} {scriptStats.lines === 1 ? 'sentence' : 'sentences'}
                  {#if scriptStats.speakers > 0}· {scriptStats.speakers} {scriptStats.speakers === 1 ? 'speaker' : 'speakers'}{/if}
                  {#if scriptStats.notes > 0}· {scriptStats.notes} stage {scriptStats.notes === 1 ? 'direction' : 'directions'}{/if}
                  –
                {/if}
                In show mode ↑/↓ step from speech to speech; <span class="mono">**text**</span> is a stage direction,
                a line <span class="mono">//## Name (info)##</span> a light marker with light of its own.
                <button class="btn small preview-script" type="button" onclick={() => (scriptPreviewOpen = true)}
                  disabled={!form.script.trim()} title="Shows the script as the show displays it – with unsaved changes">
                  👁 Preview
                </button>
              </p>
            <label class="check">
              <input type="checkbox" bind:checked={form.autoFollow} />
              Continue automatically after
              <input class="seconds" type="number" min="0.1" max="3600" step="0.1" bind:value={form.autoFollowSeconds}
                disabled={!form.autoFollow} /> s
            </label>
            <p class="hint dim">The time starts when the fade-in has finished. Without it the scene stays until GO.</p>
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
          </form>

          <section>
            <h3>On stage</h3>
            <div class="buttons">
              <button class="btn" type="button" onclick={() => selected && loadIntoProgrammer(selected)}>Load into programmer</button>
              {#if stagePreview.status.sceneId === selected.id}
                <button class="btn primary" type="button" onclick={() => stagePreview.stop()}
                  title="Stop playing this scene on stage">■ Stop showing</button>
              {:else}
                <button class="btn" type="button" onclick={() => projectId !== null && stagePreview.showScene(projectId, selected.id)}
                  title="Play the scene with its compositions on stage, above the playback">▶ Show on stage</button>
              {/if}
              <button class="btn" type="button"
                onclick={() => projectId !== null && selected && stagePreview.rehearseFade(projectId, selected.id)}
                title="Play the GO into this scene on stage: from {previousScene ? `scene ${previousScene.number}` : 'the dark stage'} with the fade times of the show">
                ⇢ Rehearse fade
              </button>
              <button class="btn record-outline" type="button" onclick={() => (overwriteOpen = true)}
                disabled={!programmerHasValues || !canQuickUpdate}
                title={canQuickUpdate
                  ? 'Take the programmer into the look of this scene'
                  : 'The first composition has several steps – change its looks in the composition'}>⏺ Update from programmer</button>
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
            <SceneCompositions {projectId} scene={selected} {scenes} {compositions} {looks} {fixtures} {groups} {modes} />
            {#each sceneMarkers as marker (marker.name)}
              <SceneCompositions {projectId} scene={selected} {scenes} {compositions} {looks} {fixtures} {groups} {modes}
                marker={marker.name} info={marker.info} />
            {/each}
            <SceneKeys {projectId} scene={selected} {compositions} />
          {/if}

          <footer class="editor-foot">
            <button class="btn small" type="button" onclick={duplicate}
              title="A new scene right after this one with the same compositions, keys, fade times and script">⧉ Duplicate</button>
            <button class="btn small danger" type="button" onclick={() => (deleteOpen = true)}>Delete scene</button>
            <button class="btn primary save" type="submit" form="scene-form"
              title="Saves number, name, cue note, script, auto-follow and fade times">Save</button>
          </footer>
        {/if}
      </aside>
    </div>
  </section>
{/if}

<RecordSceneModal bind:open={recordOpen} suggestedNumber={nextNumber}
  onrecorded={(scene) => project && load(project.id).then(() => select(scene))} />

<UpdateFromProgrammerModal bind:open={overwriteOpen}
  subject={baseLook ? `the look of scene ${selected?.number} “${selected?.name}”` : `scene ${selected?.number} “${selected?.name}”`}
  note={baseLook && baseLook.owner === null ? 'It is a shared look – it changes everywhere it is played.' : undefined}
  onconfirm={overwrite} />

<Modal bind:open={scriptPreviewOpen} title="Script {form.number} {form.name}" wide>
  <div class="script-preview">
    <ScriptText script={parseScript(form.script)} />
  </div>
  <p class="hint dim">As in show mode – {scriptStats.lines} {scriptStats.lines === 1 ? 'sentence' : 'sentences'} to step through.</p>
</Modal>

<Modal bind:open={deleteOpen} title="Delete scene">
  <p>Delete scene <strong>{selected?.number} “{selected?.name}”</strong>?</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (deleteOpen = false)}>Cancel</button>
    <button class="btn danger" type="button" onclick={remove}>Delete</button>
  {/snippet}
</Modal>

<style>
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

  .preview-script {
    margin-left: 6px;
  }

  /* the size of the script in show mode */
  .script-preview {
    padding: 4px 6px 12px;
    font-size: 20px;
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
    /* a narrow list, most of the room for the selected scene: script, compositions, keys */
    grid-template-columns: minmax(0, 3fr) minmax(0, 7fr);
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

  /* always reachable at the bottom of the scrolling editor */
  .editor-foot {
    position: sticky;
    bottom: -16px;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin: 0 -16px -16px;
    padding: 12px 16px;
    border-top: 1px solid var(--border);
    background: var(--panel-solid);
    border-radius: 0 0 var(--radius) var(--radius);
  }

  .editor-foot .save {
    margin-left: auto;
    min-width: 120px;
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
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
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
