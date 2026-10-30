<script lang="ts">
  import FadersPanel from '../components/FadersPanel.svelte';
  import Modal from '../components/Modal.svelte';
  import RecordSceneModal from '../components/RecordSceneModal.svelte';
  import SceneCompositions from '../components/SceneCompositions.svelte';
  import SceneKeys from '../components/SceneKeys.svelte';
  import ScriptEditor from '../components/ScriptEditor.svelte';
  import ScriptText from '../components/ScriptText.svelte';
  import { api } from '../lib/api';
  import { modesOf, swatchesOf } from '../lib/color';
  import { live } from '../lib/live.svelte';
  import { navigate } from '../lib/router.svelte';
  import { app } from '../lib/state.svelte';
  import { notify, showError } from '../lib/toasts.svelte';
  import { compositionIcon, lookMap, sceneStartValues } from '../lib/compositions';
  import { numberAt, numberBetween } from '../lib/sceneNumbers';
  import { parseScript, stops } from '../lib/script';
  import { curveLabel, FADE_CURVES, sceneRequestOf, secondsToMs, timingLabel } from '../lib/sceneTiming';
  import { stagePreview } from '../lib/stagePreview.svelte';
  import type { Composition, FadeCurve, Fixture, Group, Look, Move, Scene } from '../lib/types';

  let scenes = $state<Scene[]>([]);
  let fixtures = $state<Fixture[]>([]);
  let groups = $state<Group[]>([]);
  let selectedId = $state<number | null>(null);
  let recordOpen = $state(false);
  let deleteOpen = $state(false);
  let scriptPreviewOpen = $state(false);
  let compositions = $state<Composition[]>([]);
  let looks = $state<Look[]>([]);
  let moves = $state<Move[]>([]);
  let form = $state({
    number: '',
    name: '',
    cueNote: '',
    autoFollow: false,
    autoFollowSeconds: 5,
    // empty (null) = the project's time
    delaySeconds: null as number | null,
    // empty = the project's curve
    fadeCurve: '' as FadeCurve | '',
    moveInDark: false,
    dimOutSeconds: null as number | null,
    travelSeconds: null as number | null,
    script: '',
  });
  let fades = $state({ fadeIn: 3, fadeOut: 3, fadeCurve: 'linear' as FadeCurve, dimOut: 0.5, travel: 1.5 });

  const project = $derived(app.active);
  // effects depend on the id only: reloading the project list yields new objects for the same project
  const projectId = $derived(app.active?.id ?? null);
  const fadeInMs = $derived(app.active?.fadeInMs ?? null);
  const fadeOutMs = $derived(app.active?.fadeOutMs ?? null);
  const dimOutMs = $derived(app.active?.dimOutMs ?? null);
  const travelMs = $derived(app.active?.travelMs ?? null);
  const fadeCurve = $derived(app.active?.fadeCurve ?? null);
  const selected = $derived(scenes.find((s) => s.id === selectedId) ?? null);
  /** light markers of the script being edited, each name once: every one gets light of its own */
  const sceneMarkers = $derived.by(() => {
    const seen = new Set<string>();
    return parseScript(form.script).markers.filter((m) => !seen.has(m.name) && seen.add(m.name));
  });
  const scriptStats = $derived.by(() => {
    const script = parseScript(form.script);
    return { lines: script.lineCount - script.pauseCount, notes: script.noteCount, pauses: script.pauseCount,
      speakers: script.speakers.length, stops: stops(script).length };
  });
  const compositionsById = $derived(new Map(compositions.map((c) => [c.id, c])));
  const looksById = $derived(lookMap(looks));
  const modes = $derived(modesOf(fixtures, (id) => app.fixtureType(id)));
  const nextNumber = $derived(
    scenes.length === 0 ? '1' : String(Math.floor(Number(scenes[scenes.length - 1].number)) + 1),
  );

  $effect(() => {
    void live.scenesRevision;
    void live.patchRevision;
    void live.compositionsRevision;
    void live.looksRevision;
    void live.movesRevision;
    if (projectId !== null) load(projectId);
  });

  $effect(() => {
    // only when the stored fade times change, so unsaved edits survive unrelated project reloads
    if (fadeInMs !== null && fadeOutMs !== null && fadeCurve !== null && dimOutMs !== null && travelMs !== null) {
      fades = { fadeIn: fadeInMs / 1000, fadeOut: fadeOutMs / 1000, fadeCurve, dimOut: dimOutMs / 1000,
        travel: travelMs / 1000 };
    }
  });

  async function load(projectId: number) {
    try {
      [scenes, fixtures, groups, compositions, looks, moves] = await Promise.all([
        api.scenes.list(projectId),
        api.fixtures.list(projectId),
        api.groups.list(projectId),
        api.compositions.list(projectId),
        api.looks.list(projectId),
        api.moves.list(projectId),
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
      delaySeconds: scene.delayMs > 0 ? scene.delayMs / 1000 : null,
      fadeCurve: scene.fadeCurve ?? '',
      moveInDark: scene.moveInDark,
      dimOutSeconds: scene.dimOutMs !== null ? scene.dimOutMs / 1000 : null,
      travelSeconds: scene.travelMs !== null ? scene.travelMs / 1000 : null,
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
        // fade in and out are set in the frame of the start and saved there at once
        fadeInMs: selected.fadeInMs,
        fadeOutMs: selected.fadeOutMs,
        delayMs: secondsToMs(form.delaySeconds),
        fadeCurve: form.fadeCurve || null,
        moveInDark: form.moveInDark,
        dimOutMs: secondsToMs(form.dimOutSeconds),
        travelMs: secondsToMs(form.travelSeconds),
        script: form.script,
      });
      notify(`Saved scene ${scene.number}`);
      await load(project.id);
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
    // the list scrolls on its own: dragging to its edge scrolls it
    const box = listElement.getBoundingClientRect();
    if (event.clientY < box.top + 40) listElement.scrollBy(0, -16);
    else if (event.clientY > box.bottom - 40) listElement.scrollBy(0, 16);
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
        dimOutMs: Math.round(fades.dimOut * 1000),
        travelMs: Math.round(fades.travel * 1000),
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
      </div>
      <form class="fades glass" onsubmit={(e) => { e.preventDefault(); saveFades(); }}>
        <label>Fade in <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeIn} /> s</label>
        <label>Fade out <input type="number" min="0" max="600" step="0.1" bind:value={fades.fadeOut} /> s</label>
        <label>Curve
          <select bind:value={fades.fadeCurve}>
            {#each FADE_CURVES as curve (curve.id)}<option value={curve.id} title={curve.hint}>{curve.label}</option>{/each}
          </select>
        </label>
        <label title="Move in dark: time to dim out the heads that move">Dim out
          <input type="number" min="0" max="60" step="0.1" bind:value={fades.dimOut} /> s</label>
        <label title="Move in dark: time the heads get to move while dark">Travel
          <input type="number" min="0" max="60" step="0.1" bind:value={fades.travel} /> s</label>
        <button class="btn small" type="submit"
          disabled={fades.fadeIn * 1000 === project.fadeInMs && fades.fadeOut * 1000 === project.fadeOutMs
            && fades.fadeCurve === project.fadeCurve && fades.dimOut * 1000 === project.dimOutMs
            && fades.travel * 1000 === project.travelMs}>Save</button>
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
              <div class="field">
                <span>Script</span>
                <ScriptEditor bind:value={form.script} rows={12}
                  placeholder={'Text spoken on stage during this scene.\nJuliet: Ay me! – a name and a colon at the start of a line marks who speaks.\n**Enter Juliet** – stage directions in two asterisks.\n//## Window (moonlight) ## – a light marker: the light of the scene changes here.\n//|| Long pause || – a pause: stepping stops here.'} />
              </div>
              <p class="hint dim">
                {#if scriptStats.lines > 0 || scriptStats.notes > 0 || scriptStats.pauses > 0}
                  {scriptStats.lines} {scriptStats.lines === 1 ? 'sentence' : 'sentences'}
                  {#if scriptStats.speakers > 0}· {scriptStats.speakers} {scriptStats.speakers === 1 ? 'speaker' : 'speakers'}{/if}
                  {#if scriptStats.notes > 0}· {scriptStats.notes} stage {scriptStats.notes === 1 ? 'direction' : 'directions'}{/if}
                  {#if scriptStats.pauses > 0}· {scriptStats.pauses} {scriptStats.pauses === 1 ? 'pause' : 'pauses'}{/if}
                  –
                {/if}
                In show mode ↑/↓ step from speech to speech; <span class="mono">**text**</span> is a stage direction,
                a line <span class="mono">//## Name (info)##</span> a light marker with light of its own. A marker with
                only stage directions after it is a stop of its own: the light changes there and waits. A line
                <span class="mono">//|| Pause ||</span> is a pause: nobody speaks, but stepping stops there.
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
                <legend>GO into this scene</legend>
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
                Fade in and out are set in the frames below: the start frame for the GO into the scene, each light marker
                for its own change. “Delay” holds back the fade-in of the GO – e.g. Fade in 0 and Fade out 0 for a flash.
              </p>
              <fieldset class="timing">
                <legend>Moving heads</legend>
                <label class="check"><input type="checkbox" bind:checked={form.moveInDark} /> Move in dark</label>
                <label>Dim out <input class="seconds" type="number" min="0" max="60" step="0.1"
                  bind:value={form.dimOutSeconds} placeholder={String(project.dimOutMs / 1000)}
                  disabled={!form.moveInDark} /> s</label>
                <label>Travel <input class="seconds" type="number" min="0" max="60" step="0.1"
                  bind:value={form.travelSeconds} placeholder={String(project.travelMs / 1000)}
                  disabled={!form.moveInDark} /> s</label>
              </fieldset>
              <p class="hint dim">
                Heads that point somewhere else in this scene dim out, move while dark and dim in with the fade-in,
                instead of sweeping across the stage while lit. Heads staying where they are fade as usual.
              </p>
          </form>

          {#if projectId !== null}
            <SceneCompositions {projectId} scene={selected} {scenes} {compositions} {looks} {moves} {fixtures} {groups}
              {modes} hasMarkers={sceneMarkers.length > 0} />
            {#each sceneMarkers as marker (marker.name)}
              <SceneCompositions {projectId} scene={selected} {scenes} {compositions} {looks} {moves} {fixtures} {groups}
                {modes} marker={marker.name} info={marker.info} />
            {/each}
            <SceneKeys {projectId} scene={selected} {compositions} {moves} />
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

    <p class="dim help">
      The cue list of the show, ordered by number. Drag a scene by ⋮⋮ to move it – it gets a number between its new
      neighbors (like 2.5). “+ Empty scene” inserts after the selected scene.
    </p>
  </section>
{/if}

<RecordSceneModal bind:open={recordOpen} suggestedNumber={nextNumber}
  onrecorded={(scene) => project && load(project.id).then(() => select(scene))} />

<Modal bind:open={scriptPreviewOpen} title="Script {form.number} {form.name}" wide>
  <div class="script-preview">
    <ScriptText script={parseScript(form.script)} />
  </div>
  <p class="hint dim">As in show mode – {scriptStats.stops} {scriptStats.stops === 1 ? 'stop' : 'stops'} to step through
    (speeches, pauses, and light markers without speech or pause after them).</p>
</Modal>

<Modal bind:open={deleteOpen} title="Delete scene">
  <p>Delete scene <strong>{selected?.number} “{selected?.name}”</strong>?</p>
  {#snippet actions()}
    <button class="btn ghost" type="button" onclick={() => (deleteOpen = false)}>Cancel</button>
    <button class="btn danger" type="button" onclick={remove}>Delete</button>
  {/snippet}
</Modal>

<style>
  .help {
    margin: 0;
    max-width: 820px;
    font-size: 12px;
  }

  .preview-script {
    margin-left: 6px;
  }

  /* the size of the script in show mode */
  .script-preview {
    padding: 4px 6px 12px;
    font-size: 20px;
  }

  .view {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 12px 8px;
    /* fits between the top bar and the master bar: the heading stays, the list and the editor scroll on their own */
    height: calc(100vh - 172px);
    min-height: 420px;
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
    flex: 1;
    min-height: 0;
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
    max-height: 100%;
    overflow-y: auto;
    /* hidden labels (absolute) stay inside the scrolling list instead of lengthening the page */
    position: relative;
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
    /* scrolls on its own, Save stays at the bottom */
    max-height: 100%;
    overflow-y: auto;
    position: relative;
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
    /* one column: the page scrolls as a whole */
    .view {
      height: auto;
    }

    .layout {
      grid-template-columns: minmax(0, 1fr);
    }

    .list,
    .editor {
      max-height: none;
      overflow-y: visible;
    }
  }
</style>
